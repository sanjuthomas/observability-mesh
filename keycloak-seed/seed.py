#!/usr/bin/env python3
"""Load users from users.yaml into Keycloak via the Admin REST API."""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

import httpx
import yaml


def admin_token(base_url: str, admin_user: str, admin_password: str) -> str:
    with httpx.Client(timeout=30.0) as client:
        response = client.post(
            f"{base_url.rstrip('/')}/realms/master/protocol/openid-connect/token",
            data={
                "grant_type": "password",
                "client_id": "admin-cli",
                "username": admin_user,
                "password": admin_password,
            },
        )
    response.raise_for_status()
    token = response.json().get("access_token")
    if not token:
        raise RuntimeError("Keycloak admin login failed")
    return token


class KeycloakAdminClient:
    def __init__(self, base_url: str, realm: str, token: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.realm = realm
        self.headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    def _request(
        self,
        method: str,
        path: str,
        *,
        json_body: dict[str, Any] | None = None,
        expected: tuple[int, ...] = (200, 204),
    ) -> Any:
        with httpx.Client(timeout=30.0) as client:
            response = client.request(
                method,
                f"{self.base_url}{path}",
                headers=self.headers,
                json=json_body,
            )
        if response.status_code not in expected:
            detail = response.text.strip() or response.reason_phrase
            raise RuntimeError(f"{method} {path} failed ({response.status_code}): {detail}")
        if not response.content:
            return None
        return response.json()

    def find_user(self, username: str) -> dict[str, Any] | None:
        users = self._request(
            "GET",
            f"/admin/realms/{self.realm}/users?username={username}&exact=true",
        )
        return users[0] if users else None

    def create_user(self, payload: dict[str, Any]) -> str | None:
        with httpx.Client(timeout=30.0) as client:
            response = client.post(
                f"{self.base_url}/admin/realms/{self.realm}/users",
                headers=self.headers,
                json=payload,
            )
        if response.status_code == 201:
            location = response.headers.get("Location", "")
            return location.rsplit("/", 1)[-1] if location else None
        if response.status_code == 409:
            existing = self.find_user(payload["username"])
            return existing["id"] if existing else None
        detail = response.text.strip() or response.reason_phrase
        raise RuntimeError(f"POST /users failed ({response.status_code}): {detail}")

    def update_user(self, user_id: str, payload: dict[str, Any]) -> None:
        self._request(
            "PUT",
            f"/admin/realms/{self.realm}/users/{user_id}",
            json_body=payload,
        )

    def reset_password(self, user_id: str, password: str) -> None:
        self._request(
            "PUT",
            f"/admin/realms/{self.realm}/users/{user_id}/reset-password",
            json_body={"type": "password", "value": password, "temporary": False},
        )


def attributes_for_user(user: dict[str, Any]) -> dict[str, list[str]]:
    attrs: dict[str, list[str]] = {
        "subject_user_id": [user["user_id"]],
        "title": [user["title"]],
        "roles": [json.dumps(user.get("roles") or [], separators=(",", ":"))],
        "groups": [json.dumps(user.get("groups") or [], separators=(",", ":"))],
    }
    if user.get("lob") is not None:
        attrs["lob"] = [user["lob"]]
    if user.get("supervisor_id") is not None:
        attrs["supervisor_id"] = [user["supervisor_id"]]
    if user.get("covering_lobs"):
        attrs["covering_lobs"] = [json.dumps(user["covering_lobs"], separators=(",", ":"))]
    return attrs


def user_payload(user: dict[str, Any], *, password: str, email_domain: str) -> dict[str, Any]:
    return {
        "username": user["user_id"],
        "enabled": True,
        "firstName": user["given_name"],
        "lastName": user["family_name"],
        "email": f"{user['user_id']}@{email_domain}",
        "emailVerified": True,
        "attributes": attributes_for_user(user),
        "credentials": [{"type": "password", "value": password, "temporary": False}],
    }


def seed_users(client: KeycloakAdminClient, seed: dict[str, Any], *, dry_run: bool = False) -> None:
    defaults = seed.get("defaults") or {}
    password = defaults.get("password", "Password1!")
    email_domain = defaults.get("email_domain", "ssi.local")
    for user in seed.get("users") or []:
        action = "create"
        existing = None if dry_run else client.find_user(user["user_id"])
        if existing:
            action = "update"
        print(
            f"[{action}] {user['user_id']}: {user['given_name']} {user['family_name']} "
            f"({user['title']}) roles={','.join(user.get('roles') or [])}"
        )
        if dry_run:
            continue
        payload = user_payload(user, password=password, email_domain=email_domain)
        if existing:
            user_id = existing["id"]
            payload.pop("credentials", None)
            client.update_user(user_id, payload)
            client.reset_password(user_id, password)
        else:
            user_id = client.create_user(payload)
            if user_id is None:
                existing = client.find_user(user["user_id"])
                if existing:
                    user_id = existing["id"]
                    payload.pop("credentials", None)
                    client.update_user(user_id, payload)
                    client.reset_password(user_id, password)


def main() -> int:
    parser = argparse.ArgumentParser(description="Seed Keycloak users from YAML")
    parser.add_argument(
        "--file",
        type=Path,
        default=Path(__file__).with_name("users.yaml"),
        help="Path to users.yaml",
    )
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    base_url = os.environ.get("KEYCLOAK_URL", "http://localhost:9080")
    realm = os.environ.get("KEYCLOAK_REALM", "observability-mesh")
    admin_user = os.environ.get("KEYCLOAK_ADMIN", "admin")
    admin_password = os.environ.get("KEYCLOAK_ADMIN_PASSWORD", "admin")

    seed = yaml.safe_load(args.file.read_text(encoding="utf-8"))
    if args.dry_run:
        seed_users(KeycloakAdminClient(base_url, realm, "dry-run"), seed, dry_run=True)
        print(f"Done ({len(seed.get('users') or [])} users, dry-run).")
        return 0

    token = admin_token(base_url, admin_user, admin_password)
    client = KeycloakAdminClient(base_url, realm, token)
    seed_users(client, seed)
    print(f"Done ({len(seed.get('users') or [])} users).")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit(130) from None
    except Exception as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
