# Guardrails Service

A high-performance Go service for validating HTTP request/response payloads against guardrail policies, deployed on Cloudflare Workers with container support.

## 🎯 Overview

This service validates API traffic against guardrail policies, detects threats, and reports them to the Akto dashboard. It's designed for deployment on Cloudflare's edge network for low latency and global scalability.

### Key Features

✅ **Policy-Based Validation** - Validates payloads using akto-gateway library
✅ **Threat Detection** - Automatically detects and reports security threats
✅ **Edge Deployment** - Runs on Cloudflare's global network
✅ **Auto-Scaling** - Scales automatically with traffic
✅ **Mini-Runtime Compatible** - Drop-in replacement for mini-runtime-service validation

## 🚀 Quick Start

```bash
# 1. Build and push container
cd container
docker build -t guardrails-service:latest .
docker push registry.cloudflare.com/${ACCOUNT_ID}/guardrails-service:latest

# 2. Deploy worker
cd ../worker
npm install
wrangler secret put DATABASE_ABSTRACTOR_SERVICE_TOKEN
wrangler secret put THREAT_BACKEND_TOKEN
wrangler deploy

# 3. Test
curl https://guardrails-service.your-subdomain.workers.dev/health
```

See [QUICKSTART.md](./QUICKSTART.md) for detailed instructions.

## 📂 Project Structure

```
guardrails-service/
│
├── worker/                    # Cloudflare Worker (TypeScript)
│   ├── src/
│   │   └── index.ts          # Worker proxy to container
│   ├── wrangler.yaml         # Worker configuration
│   ├── package.json          # Node.js dependencies
│   └── tsconfig.json         # TypeScript config
│
├── container/                 # Go Service Container
│   ├── src/
│   │   ├── main.go           # Application entry point
│   │   ├── handlers/         # HTTP request handlers
│   │   ├── models/           # Data models
│   │   └── pkg/              # Internal packages
│   │       ├── auth/         # JWT authentication
│   │       ├── config/       # Configuration
│   │       ├── dbabstractor/ # Policy fetching
│   │       └── validator/    # Validation logic
│   ├── Dockerfile            # Container image
│   ├── .env.example          # Environment template
│   ├── nginx-demo.env        # Demo configuration
│   ├── README.md             # Container docs
│   └── SETUP.md              # Setup guide
│
├── DEPLOYMENT.md             # Deployment guide
├── QUICKSTART.md             # Quick start guide
└── README.md                 # This file
```

## 🏗️ Architecture

```
┌──────────────────────────┐
│  Cloudflare Edge Network │
│                          │
│  ┌────────────────────┐  │
│  │  Worker (Proxy)    │  │──────┐
│  └────────────────────┘  │      │
└──────────────────────────┘      │
                                  ▼
                    ┌──────────────────────────┐
                    │  Container (Go Service)  │
                    │                          │
                    │  • Fetch Policies        │
                    │  • Validate Payloads     │
                    │  • Report Threats        │
                    └──────────────────────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    │             │             │
                    ▼             ▼             ▼
            ┌──────────┐   ┌──────────┐  ┌─────────┐
            │ Database │   │  Agent   │  │ Threat  │
            │Abstractor│   │  Guard   │  │ Backend │
            │(Policies)│   │  Engine  │  │(Dashboard)│
            └──────────┘   └──────────┘  └─────────┘
```

## 📋 API Endpoints

### Health Check
```bash
GET /health              # Container health
GET /worker-health       # Worker health
```

### Validation (Mini-Runtime Compatible)
```bash
POST /api/ingestData
Content-Type: application/json

{
  "batchData": [{
    "path": "/api/users",
    "method": "POST",
    "requestPayload": "{\"user\":\"john\"}",
    "responsePayload": "{\"id\":123}",
    "ip": "192.168.1.1",
    "statusCode": "200"
  }]
}
```

### Individual Validation
```bash
POST /api/validate/request
POST /api/validate/response
```

## 🔧 Configuration

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `DATABASE_ABSTRACTOR_SERVICE_TOKEN` | ✅ | JWT token for policy fetching |
| `THREAT_BACKEND_TOKEN` | ✅ | Token for threat reporting |
| `DATABASE_ABSTRACTOR_SERVICE_URL` | ✅ | Database abstractor URL |
| `THREAT_BACKEND_URL` | ✅ | Threat backend URL |
| `SCANNER_API_URL` | ❌ | agent-guard base URL for text scans and image OCR (default: Akto SaaS) |
| `SERVER_PORT` | ❌ | Port (default: 8080) |
| `LOG_LEVEL` | ❌ | Log level (default: info) |

See [container/.env.example](./container/.env.example) for template.

## 🔍 How It Works

1. **Receive Traffic** - Worker receives HTTP requests
2. **Forward to Container** - Worker forwards to Go container
3. **Fetch Policies** - Container fetches guardrail policies from DB abstractor
4. **Validate** - Container validates request/response using akto-gateway library
5. **Report Threats** - Container reports detected threats to dashboard
6. **Return Result** - Worker returns validation result to client

## 🛠️ Development

### Local Development

```bash
# Terminal 1: Run container
cd container/src
set -a && source ../nginx-demo.env && set +a
go run main.go

# Terminal 2: Run worker
cd worker
wrangler dev
```

### Build Container

```bash
cd container
docker build -t guardrails-service:latest .
```

### Test Worker

```bash
cd worker
npm install
npm run dev
```

## 📚 Documentation

- **[QUICKSTART.md](./QUICKSTART.md)** - Get started in 5 minutes
- **[DEPLOYMENT.md](./DEPLOYMENT.md)** - Complete deployment guide
- **[container/README.md](./container/README.md)** - Container documentation
- **[container/SETUP.md](./container/SETUP.md)** - Architecture and setup

## 🚢 Deployment

### Prerequisites
- Cloudflare account with Workers paid plan
- Docker installed
- Wrangler CLI installed
- Node.js and npm installed

### Deploy to Cloudflare

```bash
# 1. Build container
cd container
docker build -t guardrails-service:latest .

# 2. Push to Cloudflare registry
ACCOUNT_ID=$(wrangler whoami | grep "Account ID" | awk '{print $3}')
docker tag guardrails-service:latest registry.cloudflare.com/${ACCOUNT_ID}/guardrails-service:latest
docker push registry.cloudflare.com/${ACCOUNT_ID}/guardrails-service:latest

# 3. Deploy worker
cd ../worker
npm install
wrangler secret put DATABASE_ABSTRACTOR_SERVICE_TOKEN
wrangler secret put THREAT_BACKEND_TOKEN
wrangler deploy
```

See [DEPLOYMENT.md](./DEPLOYMENT.md) for detailed instructions.

## 📊 Monitoring

```bash
# View worker logs
wrangler tail

# View container logs
wrangler tail --name guardrails-service-container

# View metrics
Visit: https://dash.cloudflare.com → Workers & Pages → guardrails-service
```

## 🧪 Testing

```bash
# Test health endpoint
curl https://your-worker.workers.dev/health

# Test validation
curl -X POST https://your-worker.workers.dev/api/ingestData \
  -H "Content-Type: application/json" \
  -d @test-payload.json
```

## 🐛 Troubleshooting

### Container not starting?
```bash
# Check logs
wrangler tail --name guardrails-service-container

# Verify environment
wrangler secret list
```

### Worker can't reach container?
- Check service binding in `worker/wrangler.yaml`
- Verify container service is deployed
- Test container health directly

### Validation failing?
- Verify DATABASE_ABSTRACTOR_SERVICE_TOKEN is valid
- Check THREAT_BACKEND_TOKEN is set
- Ensure Agent Guard Engine URL is accessible

See [DEPLOYMENT.md#troubleshooting](./DEPLOYMENT.md#troubleshooting) for more.

## 💰 Cost Estimation

### Cloudflare Workers
- First 100,000 requests/day: **Free**
- Additional requests: **$0.50 per million**
- Workers paid plan: **~$5/month**

### Container
- CPU time: **$0.12 per million GB-seconds**
- Network egress: **$0.09 per GB**

Typical cost for 1M requests/day: **$5-15/month**

## 🔒 Security

- ✅ Distroless container image
- ✅ Non-root user
- ✅ Secrets management with Wrangler
- ✅ HTTPS by default
- ✅ Rate limiting (configurable)
- ✅ Environment variable encryption

## 📈 Performance

- **Latency**: <50ms (edge deployment)
- **Throughput**: 1000+ req/s per instance
- **Availability**: 99.99% (Cloudflare SLA)
- **Auto-scaling**: Instant

## 🤝 Contributing

See the main Akto repository for contribution guidelines.

## 📄 License

See parent repository for license information.

## 🆘 Support

- **Documentation**: See docs in this directory
- **Logs**: `wrangler tail`
- **Cloudflare Docs**: https://developers.cloudflare.com/workers/
- **Akto Support**: Contact development team

## 🎯 Next Steps

1. ✅ Follow [QUICKSTART.md](./QUICKSTART.md) to deploy
2. Configure custom domain
3. Set up monitoring and alerts
4. Enable rate limiting
5. Configure CI/CD pipeline
6. Set up staging environment

---

**Built with** ❤️ **by Akto** | Powered by Cloudflare Workers
