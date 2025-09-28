# Docker Build Troubleshooting (Windows/WSL)

## Symptom: failed to fetch anonymous token from Docker Hub
```
failed to solve: failed to fetch anonymous token: Get "https://auth.docker.io/token?...": connectex: A connection attempt failed ...
```

This is usually a Docker Hub connectivity issue (IPv6/ISP/proxy). Try the following options in order:

## Option A: Use the MCR-based Dockerfile (no Docker Hub)
Build using Microsoft Container Registry images instead of Docker Hub:
```bash
# In repo root
docker build -t compensation-backend:mcr -f Dockerfile.mcr .
# Run with compose services (MySQL/Redis), pointing to host network services or compose
```

Or with compose override:
```bash
# Use the alternate file explicitly for app service only
DOCKER_BUILDKIT=1 docker build -t compensation-backend:mcr -f Dockerfile.mcr .
```
Then edit docker-compose.yml `app.image: compensation-backend:mcr` (or run `docker run ...`).

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
docker compose build --no-cache app && docker compose up -d app
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

