# Agent Guard - Production Guide

## Quick Deploy

```bash
cd apps/agent-guard
docker-compose up -d
```

**Services:**
- Go API: http://localhost:8091
- Python Service: http://localhost:8092 (internal)

---

## Performance Optimization

### Volume-Based Model Caching (Enabled)

**Setup:**
```yaml
volumes:
  - model-cache:/app/.cache/huggingface  # Persists ONNX models
```

**Benefits:**
- ✓ Models persist across container restarts
- ✓ No re-download after `docker-compose down`
- ✓ Shared cache if scaled horizontally

**First Time:**
```bash
docker-compose up -d
# First scan: ~30s (downloads to volume)
# Next scans: ~500ms (uses cache)
```

**After Restart:**
```bash
docker-compose restart
# First scan: ~500ms (uses volume!) ⚡
```

### Pre-Warm Cache (Optional)

**Manual warmup (recommended):**
```bash
docker-compose up -d
docker exec agent-guard-python python warmup.py
# Downloads all 7 ONNX models (~2-3 minutes)
# Then instant for all subsequent scans
```

**Auto-warmup on build:**
```dockerfile
# Uncomment in python-service/Dockerfile:
RUN python warmup.py
```
Build time increases by ~3-5 minutes, but container starts with all models ready.

---

## API Endpoints

### Health Check
```bash
GET /health
```

### Single Scan
```bash
POST /scan
{
  "scanner_type": "prompt",
  "scanner_name": "Toxicity",
  "text": "Your text"
}
```

### Parallel Scan (Batch)
```bash
POST /scan/parallel
{
  "text": "Your text",
  "scanners": [
    {"scanner_type": "prompt", "scanner_name": "Toxicity"},
    {"scanner_type": "prompt", "scanner_name": "Secrets"}
  ]
}
```

### Streaming Scan (Real-time)
```bash
POST /scan/stream
{
  "text": "Your text",
  "scanners": [...]
}
```

See [EXAMPLES.md](EXAMPLES.md) for all scanner curl commands.

---

## Performance Metrics

| Metric | Value |
|--------|-------|
| First scan (cold) | ~30s (one-time model download) |
| Cached scan | ~500ms (56x faster) |
| ONNX speedup | 2-20x vs PyTorch |
| Scanner caching | 100x speedup |
| Parallel execution | N scanners in ~max(scanner_time) |
| Throughput | ~100 req/sec |

---

## Makefile Commands

```bash
make up       # Start services with health check
make down     # Stop services (keeps cache)
make restart  # Quick restart
make logs     # Follow logs
make health   # Check both services
make ps       # Container status
make test     # Quick API test
make clean    # Remove all (including cache)
```

---

## Production Deployment

### Docker Compose (Single Server)
```bash
docker-compose up -d
```

### Kubernetes (Scaled)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: agent-guard-python
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: python
        image: agent-guard-python:latest
        ports:
        - containerPort: 8092
        volumeMounts:
        - name: model-cache
          mountPath: /app/.cache/huggingface
      volumes:
      - name: model-cache
        persistentVolumeClaim:
          claimName: agent-guard-models
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: agent-guard-go
spec:
  replicas: 2
  template:
    spec:
      containers:
      - name: go
        image: agent-guard-go:latest
        ports:
        - containerPort: 8091
        env:
        - name: PYTHON_SERVICE_URL
          value: "http://agent-guard-python:8092"
```

---

## Monitoring

### Health Checks
```bash
curl http://localhost:8091/health  # Go orchestrator
curl http://localhost:8092/health  # Python scanner service
```

### Container Stats
```bash
docker stats agent-guard-python agent-guard-go
```

### Logs
```bash
docker-compose logs -f                    # All services
docker-compose logs -f python-service     # Python only
docker-compose logs -f go-service         # Go only
```

### Volume Status
```bash
docker volume ls | grep model-cache
docker volume inspect agent-guard_model-cache
```

---

## Configuration

### Environment Variables

**Go Service:**
- `PORT` - API port (default: 8091)
- `PYTHON_SERVICE_URL` - Python service URL (default: http://python-service:8092)
- `GIN_MODE` - Gin mode (default: release)

**Python Service:**
- `PORT` - Service port (default: 8092)
- `HF_HOME` - Hugging Face cache location (default: /app/.cache/huggingface)

### Change Ports

Edit `docker-compose.yml`:
```yaml
services:
  python-service:
    ports:
      - "9092:9092"  # External:Internal
    environment:
      - PORT=9092
  
  go-service:
    ports:
      - "9091:9091"
    environment:
      - PORT=9091
      - PYTHON_SERVICE_URL=http://python-service:9092
```

---

## Troubleshooting

### First Request Slow (30s)
**Cause:** Downloading ONNX models
**Solution:** 
```bash
docker exec agent-guard-python python warmup.py  # Pre-download all models
```

### Connection Timeout
**Cause:** Timeout too short for model download
**Current:** 120s timeout (sufficient)

### Port Conflict
**Check:**
```bash
lsof -i :8091
lsof -i :8092
```
**Fix:** Change ports in docker-compose.yml

### Models Re-downloading
**Cause:** Volume deleted
**Fix:** Don't use `docker-compose down -v` (removes volumes)
**Safe:** Use `docker-compose down` or `make down`

### Cache Not Persisting
**Check volume:**
```bash
docker volume inspect agent-guard_model-cache
docker exec agent-guard-python ls -la /app/.cache/huggingface
```

---

## Resource Requirements

**Minimum:**
- CPU: 2 cores
- RAM: 2GB
- Disk: 5GB (models + images)

**Recommended (Production):**
- CPU: 4 cores
- RAM: 4GB
- Disk: 10GB

**With Scaling (K8s):**
- Python pods: 2-3 replicas (CPU intensive)
- Go pods: 1-2 replicas (lightweight)
- Shared PVC for model cache

---

## Available Scanners

**13 Input Scanners:**
Anonymize, BanCode, BanCompetitors, BanSubstrings, BanTopics, Code, Gibberish, Language, PromptInjection⚡, Secrets, Sentiment, TokenLimit, Toxicity⚡

**14 Output Scanners:**
BanCode, BanCompetitors, BanSubstrings, BanTopics, Bias⚡, Code, Deanonymize, Language, MaliciousURLs⚡, NoRefusal⚡, Relevance⚡, Sensitive⚡, Sentiment, Toxicity⚡

**⚡ = ONNX-optimized** (7 scanners, 2-20x faster)

---

## Security Best Practices

1. **Use HTTPS** - Add reverse proxy (nginx/Traefik)
2. **Add Authentication** - JWT/API keys
3. **Rate Limiting** - Prevent abuse
4. **Network Isolation** - Keep Python service internal
5. **Resource Limits** - Set CPU/memory limits
6. **Monitoring** - Track latency and errors
7. **Backup Volume** - Backup model-cache periodically

---

## Backup & Restore

### Backup Model Cache
```bash
docker run --rm -v agent-guard_model-cache:/data \
  -v $(pwd):/backup alpine \
  tar czf /backup/model-cache-backup.tar.gz -C /data .
```

### Restore Model Cache
```bash
docker run --rm -v agent-guard_model-cache:/data \
  -v $(pwd):/backup alpine \
  tar xzf /backup/model-cache-backup.tar.gz -C /data
```

---

## Updates

### Update Service Code
```bash
# Pull latest code
git pull

# Rebuild only changed service
docker-compose build python-service  # or go-service

# Restart
docker-compose up -d
```

### Update Dependencies
```bash
# Edit requirements.txt or go.mod
# Rebuild
docker-compose build
docker-compose up -d
```

---

Built with [LLM Guard](https://github.com/protectai/llm-guard) • Part of [Akto](https://github.com/akto-api-security/akto)
