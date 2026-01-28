# SmartRoute – Project Repository

This repository contains the **SmartRoute project**, developed as part of the  
**2025ws-ase-pr-group / 25WS_ASE PR_INSO_05** course.

It is a monorepo that includes the backend, frontend, and deployment
configuration used for the SmartRoute system.

---

## Repository structure

- [`backend/`](./backend)  
  Backend service (API, business logic, persistence).  
  👉 See **[`backend/README.md`](./backend)** for setup and run instructions.

- [`frontend/`](./frontend)  
  Web frontend application.  
  👉 See **[`frontend/README.md`](./frontend)** for setup and run instructions.

- [`.gitlab-ci.yml`](./.gitlab-ci.yml)  
  CI pipeline configuration.

- [`kubernetes.yaml`](./kubernetes.yaml)  
  Kubernetes deployment configuration.

---

## Development

This repository is intended to be run **locally with backend and frontend
started separately**.

Please follow the instructions in:
- `backend/README.md`
- `frontend/README.md`

as they contain all required environment variables, dependencies, and commands.

---

## Context

SmartRoute is a **course project** and not a production system.
The focus is on software architecture, collaboration, and implementation
quality rather than long-term maintenance.

---

## License

To be defined.
