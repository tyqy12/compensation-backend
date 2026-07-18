# Docker Build Troubleshooting (Windows/WSL)

## Symptom: failed to fetch anonymous token from Docker Hub
```
failed to solve: failed to fetch anonymous token: Get "https://auth.docker.io/token?...": connectex: A connection attempt failed ...
```

This is usually a Docker Hub connectivity issue (IPv6/ISP/proxy). Try the following options in order:

## Option A: Use a reachable base-image registry
The backend and frontend now build independently. Override the image names in `.env`
when Docker Hub is unavailable:
```bash
# In repo root
REDIS_IMAGE=docker.m.daocloud.io/library/redis:7.2-alpine \
NODE_IMAGE=docker.m.daocloud.io/library/node:22-alpine \
MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17 \
JRE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:17-jre-jammy \
NGINX_IMAGE=docker.m.daocloud.io/library/nginx:1.27-alpine \
docker compose build app web
```
The image names are passed directly to Compose through the environment and do not require
editing the Compose file.

## Option B: Configure Docker Hub registry mirrors
Docker Desktop → Settings → Docker Engine, add mirrors to the JSON and restart Docker:
```json
{
  "registry-mirrors": [
    "https://dockerproxy.com",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com",
    "https://docker.mirrors.ustc.edu.cn"
  ]
}
```
Re-run:
```bash
docker compose build --no-cache app web && docker compose up -d app web
```

## Option C: Force IPv4 / disable IPv6 for Docker Desktop
Some networks block IPv6 routes. Consider disabling IPv6 in Docker Desktop experimental settings or in Windows network adapter temporarily, then retry.

## Option D: Pre-pull base images
```bash
# Try pulling base images directly
Docker pull maven:3.9-eclipse-temurin-17
Docker pull eclipse-temurin:17-jre-jammy
```
If pulls still fail, use Option A or B.

## Logs and Diagnostics
- Check Docker Desktop Dashboard → Build logs for details
- `docker info` to confirm WSL2 backend
- `docker login` may help with Hub rate limits (less likely for token issues)
