# Repository Guidelines

## Project Structure & Module Organization
- Java 17 + Spring Boot (Maven wrapper `./mvnw`).
- Source code: `src/main/java/com/yiyundao/compensation/` (layers: controller, service, mapper, entity, dto, config, security, modules such as employee, user, org, payment, approval).
- Resources: `src/main/resources/` (`application*.yml`, `mapper/` for MyBatis XML, `sql/` for schema/seed, `static/`, `templates/`).
- Tests: `src/test/java/` (JUnit 5, Spring Boot Test).

## Build, Test, and Development Commands
- Build (skip tests): `./mvnw -q clean package -DskipTests`
- Run (dev profile): `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- Run packaged JAR: `java -jar target/compensation-0.0.1-SNAPSHOT.jar`
- Test (all): `./mvnw -q test`
- Test single class: `./mvnw -q -Dtest=ClassNameTest test`

## Coding Style & Naming Conventions
- 4‑space indentation, UTF‑8, aim for ~120‑char lines.
- Packages: lower_case; Classes: UpperCamelCase; methods/fields: lowerCamelCase.
- Suffixes: `*Controller`, `*Service`, `*Mapper`, `*Config`, `*Dto`, `*Entity`, `*Enum`.
- Prefer constructor injection; use Lombok where present (`@RequiredArgsConstructor`, `@Slf4j`).
- REST returns `ApiResponse<T>`; base path `/api` (see `server.servlet.context-path`).

## Testing Guidelines
- Frameworks: JUnit 5 + Spring Boot Test; mock external calls (Redis/HTTP) and use Testcontainers for MySQL when needed.
- Test naming: `ClassNameTest` under matching package in `src/test/java`.
- Running with profiles: prefer `dev` locally; keep tests deterministic and fast.

## Commit & Pull Request Guidelines
- Conventional Commits: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `chore` (e.g., `feat(employee): add batch import API`).
- PRs must include: purpose, linked issues, how to test (commands/endpoints), config changes, and screenshots/logs for failures.
- Keep changes focused; include migration notes for DB schema updates.

## Security & Configuration Tips
- Do not commit secrets. Use env vars or an untracked `application-local.yml`.
- Review `application-*.yml` for DB/Redis/JWT/encryption keys. Enable DB migrations only where intended (e.g., `migration.audit-log.enabled`).
- Sensitive data (ID cards/bank accounts) uses encryption services—avoid logging plaintext.

## Architecture Overview (Quick)
- Core domains: employee/user/org/payment/approval; MyBatis‑Plus for persistence; RBAC via `sys_resource` + roles; approval engine drives batch workflows; payment integrates with Alipay.

## Agent-Specific Instructions (Automation)
- Use small, focused patches; prefer `apply_patch` and avoid destructive ops.
- Follow existing style and naming. Add tests for new logic when practical.
