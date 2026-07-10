# Platform compose bundle

Reusable observability mesh services included by each workload via:

```yaml
include:
  - path: ../../platform/docker-compose.yml
```

Host ports are set in the **workload** `.env` file (see `workloads/_template/.env.example`). Container names are project-scoped automatically — do not add global `container_name` entries.

See [workloads/_template/README.md](../workloads/_template/README.md) for parallel tenant setup.
