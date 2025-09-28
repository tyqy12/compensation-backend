# Docker on WSL: Run the Backend in Containers

This guide helps you run the Compensation Assistant backend in Docker on Windows with WSL2.

## 1) Prerequisites
- Windows 10/11 with WSL2 enabled (Ubuntu recommended)
- Docker Desktop for Windows (enable "Use the WSL 2 based engine")
- Enable WSL Integration for your Linux distro (Docker Desktop → Settings → Resources → WSL Integration)

## 2) Clone the repo in your WSL distro
```bash
# In Ubuntu (WSL)
cd ~
git clone <your-repo> compensation-backend
cd compensation-backend
```

Tip: avoid editing files under \\wsl$ from Windows editors without proper LF settings to prevent line-ending issues.

## 3) One-command startup with Compose
```bash
# Build and start MySQL, Redis, and the backend app
docker compose -f docker-compose.redis.yml up -d

# View logs
docker compose logs -f app
```
This starts only Redis (6379). Run the prebuilt JAR on the server directly:
```bash
java -jar target/compensation-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```
Access the API at http://localhost:8080/api/system/health. Ports: 8080 (app), 6379 (Redis).

The compose file mounts `src/main/resources/sql/schema.sql` to initialize the DB on first startup.

## 4) Environment and Secrets
The app service uses environment variables in `docker-compose.yml` to configure:
- Database: `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`
- Redis: `SPRING_DATA_REDIS_HOST/PORT`
- JWT: `JWT_SECRET`, `JWT_EXPIRATION`, `JWT_REFRESH_EXPIRATION`
- Encryption: `ENCRYPTION_SM4_KEY`, `ENCRYPTION_AES_KEY`

Change default values before production. For extra safety, place them in a `.env` file and reference from compose.

## 5) Useful Commands
```bash
# Rebuild app only
docker compose build app

# Restart app after config change
docker compose up -d app

# Tail logs
docker compose logs -f app

# DB shell
docker exec -it comp-mysql mysql -ucomp -pcomp123 compensation

# Stop and remove containers
docker compose down

# Remove volumes (CAUTION: deletes DB/Redis data)
docker compose down -v
```

## 6) Health Checks & Debugging
- Verify DB is initialized: connect to MySQL and check `employee`, `payment_*`, `sys_config`, `integration_config` tables.
- App health: GET `/api/system/health`
- Common issues:
  - Port conflicts → change host ports in compose
  - Docker Desktop not integrated with your WSL distro → enable in settings
  - Slow build on first run → maven downloads dependencies; cached in the builder layer next time

## 7) Production Notes
- Use your own managed MySQL/Redis or dedicated containers and secure credentials
- Consider multi-arch images if deploying to ARM (e.g., Apple Silicon)
- Configure resource limits for Java (see `JAVA_OPTS` in Dockerfile)
- Put secrets into an external secrets manager or Docker Swarm/K8s secrets for production

## 8) Manual docker build/run (alternative)
```bash
# Build image
docker build -t compensation-backend:latest .

# Run with envs
docker run --name comp-app \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL='jdbc:mysql://host.docker.internal:3306/compensation?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true' \
  -e SPRING_DATASOURCE_USERNAME=comp \
  -e SPRING_DATASOURCE_PASSWORD=comp123 \
  -e SPRING_DATA_REDIS_HOST=host.docker.internal \
  -e SPRING_DATA_REDIS_PORT=6379 \
  -e JWT_SECRET=change_me_super_secret_min_32_chars________ \
  compensation-backend:latest
```

On WSL, `host.docker.internal` resolves to the Windows host from Linux containers; use it if you run DB/Redis outside of Compose.
