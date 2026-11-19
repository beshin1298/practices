# Warden

Internal account management app, organised by department: login, own profile, change password, people management
with CRUD × scope permissions (EMPLOYEE < MANAGER < HR < ADMIN, many-to-many roles and departments).

- Backend: `backend/` — Spring Boot 3.5, Java 21, Spring Security 6.5, PostgreSQL 16 + Flyway, MapStruct, JWT (15 min,
  in memory) + rotating refresh token (HttpOnly cookie, SHA-256 in DB).
- Frontend: `frontend/` — React 18, TypeScript, Vite, Redux Toolkit (session only), TanStack Query + Table, Tailwind v4.

## Run

```bash
# Whole app in Docker
docker compose up -d --build                           # http://localhost:3000 (Postgres + API + nginx)
docker compose down                                    # stop; data stays in the warden-pgdata volume

# Dev with hot reload
docker compose up -d postgres                          # PostgreSQL on :5433
cd backend && ./mvnw spring-boot:run                   # API on :8080, "dev" profile, seeds demo accounts
cd frontend && npm install && npm run dev              # http://localhost:5173 (proxies /api)
bash .claude/skills/secure-feature/scripts/verify.sh   # RBAC audit + layering + backend tests + frontend build
```

Demo accounts: `admin` / `Admin@123`; `hr`, `manager`, `user`, `lan`, `dev` / `User@123`.

Profiles: `application.yml` has no secret defaults (no JWT secret, DB password, seed or Swagger) and refuses to start
without them. The `dev` profile (`application-dev.yml`) fills in public throwaway values; it is also the default when no
profile is set (IDE Run, `java -jar`). A real deployment sets `SPRING_PROFILES_ACTIVE=prod` and the values in `.env`.

## Backend layout

Feature first, then layer: `auth/`, `user/`, `role/`, `department/` each have `controller/`, `service/` (interfaces),
`service/impl/` (`XxxServiceImpl`, the only `@Service` classes), `repository/`, `entity/`, `dto/request|response/`,
`mapper/`. Shared code: `common/`, `config/`, `security/` (`jwt/`, `filter/`, `handler/`, `annotation/`, `service/`).
Calls go one way: controller -> service interface -> impl -> repository. Controllers and filters never touch a
repository, mapper, `EntityManager` or `*ServiceImpl` (verify.sh step 2 fails the build if they do).

## Rules

- Any change to an endpoint, page, route, table, column, role or permission goes through the `secure-feature` skill
  (`.claude/skills/secure-feature/SKILL.md`). Nothing is "done" until `verify.sh` is green.
- `docs/rbac-matrix.md` is the machine-readable source of truth for who may call what; keep it in sync with the
  "Thiết kế kỹ thuật" tab of the docs.
- The repo lives on `/mnt/c` under WSL2: Vite uses `watch.usePolling`; when killing processes use patterns like
  `pkill -f '[W]ardenApplication'` so the shell does not match itself.

## Docs (Claude Docs)

One doc, tabs: Requirements (overview) with one sub-tab per screen (rules, messages, test cases), Thiết kế kỹ thuật,
Schema, Skill secure-feature, Nhật ký thực hiện — https://claude.ai/artifact/TmkpfUh7J9EnWVjDCcAap2

## Sync the skill from docs

When the user says "sync skill từ docs": export the "Skill secure-feature" tab as Markdown (Claude Docs `export`),
save it, run `python3 .claude/skills/secure-feature/scripts/sync_from_docs.py <file>.md`, review the diff of
`SKILL.md`. `--check` reports drift without writing.
