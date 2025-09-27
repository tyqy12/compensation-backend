# Repository Guidelines

## Project Structure & Module Organization
- Java 17 + Spring Boot (Maven wrapper `./mvnw`).
- Source: `src/main/java/com/yiyundao/compensation/` with packages: `controller`, `service`, `mapper`, `entity`, `dto`, `config`, `security`, `adapter`.
- Resources: `src/main/resources/` — `application.yml` + profiles (`application-dev.yml`, `-staging.yml`, `-prod.yml`), `mapper/` (MyBatis XML), `sql/schema.sql`, `static/`, `templates/`.
- Tests: `src/test/java/` (JUnit 5, Spring Boot Test).

## Build, Test, and Development Commands
- Build: `./mvnw -q clean package -DskipTests`
- Run (dev): `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- Run JAR: `java -jar target/compensation-0.0.1-SNAPSHOT.jar`
- Test (all): `./mvnw -q test`
- Test one: `./mvnw -q -Dtest=PaymentBatchServiceTest test`

## Coding Style & Naming Conventions
- 4-space indent, UTF-8, aim for ~120-char lines.
- Packages lower-case; classes UpperCamelCase; methods/fields lowerCamelCase.
- Suffixes: `*Controller`, `*Service`, `*Mapper`, `*Config`, `*Dto`, `*Entity`, `*Enum`.
- Prefer constructor injection (`@RequiredArgsConstructor`); use Lombok where present (`@Slf4j`, getters/setters).
- REST responses use `ApiResponse<T>`; base path is `/api` (see `server.servlet.context-path`).

## Testing Guidelines
- Frameworks: JUnit 5 + Spring Boot Test; mock external calls; keep unit tests fast.
- Integration tests may use Testcontainers MySQL (dependency present) when DB is required.
- Naming: `ClassNameTest` for unit tests; place under matching package in `src/test/java`.

## Commit & Pull Request Guidelines
- Use Conventional Commits: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `chore` (e.g., `feat(employee): add batch import API`).
- Branches: `feature/<scope>-short-desc` or `fix/<issue-id>`.
- PRs must include: purpose, linked issues, how to test (commands/endpoints), config changes, and any screenshots or logs.

## Security & Configuration Tips
- Do not commit secrets. Prefer env vars or an untracked `application-local.yml`; review `application-*.yml` for DB, Redis, JWT, and third-party keys.
- Default: port 8080, base `/api` (e.g., `GET /api/system/health`).
- SQL scripts live in `src/main/resources/sql/`; MyBatis XML in `src/main/resources/mapper/`.
