# SSI identity seed for Keycloak (ported from policy-pilot zitadel-seed/users.yaml).

See `users.yaml` for demo personas, roles, groups, and reporting lines used by OPA and the browser UIs.

```bash
KEYCLOAK_URL=http://localhost:9080 \
KEYCLOAK_ADMIN=admin \
KEYCLOAK_ADMIN_PASSWORD=admin \
python seed.py
```

Default password for all seeded users: `Password1!`
