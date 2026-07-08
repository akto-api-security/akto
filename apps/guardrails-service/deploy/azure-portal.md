# Guardrails Service + Cache on Azure — Portal (console) walkthrough

Click-through version of [`azure.md`](./azure.md) (which has the CLI). Same
VM-based topology as GCP: one **VM Scale Set per service** behind a **load
balancer**, a **single Redis VM**, all driven by a **cloud-init custom-data**
script. Do the steps in order — later services need the private IPs of earlier ones.

**Images:** this guide pulls `aktosecurity/*` and `redis/redis-stack-server`
straight from **Docker Hub** (simplest for a console flow). If your images are
private, create a Container Registry first and add its pull credentials to the
cloud-init `docker login` line — see the ACR note in `azure.md §0`.

**Fixed private IPs** (your "internal LB" addresses, mirroring GCP `10.0.128.x`):

| Service | IP | Port |
|---|---|---|
| embedder ILB | `10.0.128.13` | 8094 |
| agent-guard ILB | `10.0.128.61` | 8090 |
| anonymizer ILB | `10.0.128.9` | 8093 |
| redis VM | `10.0.128.71` | 6379 |

---

## 1. Resource group

Portal → **Resource groups** → **Create**
- Subscription: your sub · Resource group: `rg-guardrails-prod` · Region: `East US`
- **Review + create** → **Create**

## 2. Virtual network + subnets

Portal → **Virtual networks** → **Create**
- RG `rg-guardrails-prod` · Name `vnet-guardrails` · Region `East US`
- **IP addresses** tab: address space `10.0.0.0/16`
  - Edit the default subnet → name `snet-services`, range `10.0.128.0/24`
  - **+ Add subnet** → `snet-appgw`, range `10.0.200.0/24`
- **Review + create** → **Create**

## 3. Network security group

Portal → **Network security groups** → **Create**
- RG · Name `nsg-services` · Region → **Create**
- Open `nsg-services` → **Inbound security rules** → **+ Add**
  - Source: **Service Tag** → `VirtualNetwork` · Destination: **Service Tag** → `VirtualNetwork`
  - Service: **Custom** · Port ranges `*` · Protocol **Any** · Action **Allow** · Priority `100` · Name `allow-vnet` → **Add**
- **Subnets** → **Associate** → `vnet-guardrails` / `snet-services`

## 4. Key Vault (secrets)

Portal → **Key vaults** → **Create**
- RG · Name `kv-guardrails` · Region · Permission model **Azure role-based access control** → **Create**
- Open it → **Secrets** → **+ Generate/Import** — add:
  - `redis-password` = your chosen Redis password
  - `db-abstractor-token` = the DATABASE_ABSTRACTOR_SERVICE_TOKEN
- **Access control (IAM)** → later grant each VMSS/VM managed identity the
  **Key Vault Secrets User** role (step 9).

> Don't paste secrets into Custom data — cloud-init is readable via instance
> metadata. Fetch them from Key Vault inside the startup script instead.

---

## 5. Prepare the cloud-init (Custom data) scripts

You'll paste a `#cloud-config` script into each VM/VMSS's **Advanced → Custom
data** box. The full template + the per-role `COMPOSE_FILE` / `append_env` table
is in [`azure.md §1`](./azure.md). One file per role:

| Role | `COMPOSE_FILE` | Key env appended |
|---|---|---|
| guardrails-service | `docker-compose-agentic-poc.yml` | `CACHE_MODE=decide`, `EMBEDDER_URL`, `REDIS_URL`, `SCANNER_API_URL`, `CACHE_BLOCK_DISTANCE_THRESHOLD=0.0001` |
| embedder | *(embedder compose)* | `EMBEDDER_WORKERS=<vCPU count>` |
| redis | `docker-compose-redis.yml` | Redis `--requirepass` (from Key Vault) |
| agent-guard | *(agent-guard compose)* | `ANONYMIZER_URL=http://10.0.128.9:8093`, Vertex/model config |

Have these 4 scripts ready in a text editor before creating the VMs.

---

## 6. Redis VM (single instance) — create first

Portal → **Virtual machines** → **Create** → **Azure virtual machine**
- **Basics:** RG · Name `agent-guard-redis` · Region · Image **Ubuntu Server 22.04 LTS** · Size **Standard_D2s_v5** · auth SSH key
- **Networking:** VNet `vnet-guardrails` · Subnet `snet-services` · **Public IP → None** · NSG `nsg-services`
- **Management:** **Identity → System assigned managed identity = On**
- **Advanced:** **Custom data** → paste the **redis** cloud-init
- **Review + create** → **Create**

Set the static private IP:
- Open the VM → **Networking** → the NIC → **IP configurations** → `ipconfig1`
  → **Private IP: Static**, address `10.0.128.71` → **Save** (VM restarts)

Grant Key Vault access:
- `kv-guardrails` → **Access control (IAM)** → **+ Add role assignment** →
  **Key Vault Secrets User** → assign to **Managed identity** → `agent-guard-redis`

---

## 7. Embedder — internal LB, then VMSS

### 7a. Internal Load Balancer

Portal → **Load balancers** → **Create**
- RG · Name `ilb-embedder` · Region · **SKU Standard** · **Type Internal**
- **Frontend IP configuration** → **+ Add**: VNet/subnet `snet-services`,
  Assignment **Static**, IP `10.0.128.13`, name `fe`
- **Backend pools** → **+ Add**: name `bepool` (associate to VMSS in 7b)
- **Inbound rules** → **Health probe** → **+ Add**: `p8094`, protocol **TCP**, port `8094`
- **Inbound rules** → **Load balancing rule** → **+ Add**: `r8094`, frontend `fe`,
  backend `bepool`, protocol **TCP**, port `8094`, backend port `8094`, probe `p8094`
- **Review + create** → **Create**

### 7b. VMSS

Portal → **Virtual machine scale sets** → **Create**
- **Basics:** RG · Name `vmss-embedder` · Region · **Orchestration: Uniform** ·
  Image **Ubuntu 22.04** · Size **Standard_D2s_v5** (2 vCPU → set `EMBEDDER_WORKERS=2` in cloud-init) ·
  **Availability zones: 1, 2, 3** · Instances `1`
- **Networking:** VNet/subnet `snet-services` · **no public IP** ·
  **Load balancing options → Azure load balancer** → select `ilb-embedder` / `bepool`
- **Management → Identity → System assigned = On**
- **Scaling** tab → **Scaling policy: Custom** →
  - Rule: metric **Percentage CPU** > `60` over 2 min → **Increase count by 1**
  - Rule: **Percentage CPU** < `30` over 5 min → **Decrease count by 1**
  - Min `1` · Max `6` · Default `1`
- **Advanced → Custom data** → paste the **embedder** cloud-init
- **Review + create** → **Create**
- Grant its managed identity **Key Vault Secrets User** on `kv-guardrails` (as in step 6)

`EMBEDDER_URL` for guardrails = `http://10.0.128.13:8094`.

---

## 8. agent-guard + anonymizer

Repeat step 7 twice more (ILB + VMSS each):

| Service | ILB name / IP / port | VMSS | Notes |
|---|---|---|---|
| agent-guard worker | `ilb-agentguard` / `10.0.128.61` / 8090 | `vmss-agentguard` | cloud-init: `ANONYMIZER_URL=http://10.0.128.9:8093`, Vertex/model secrets |
| anonymizer | `ilb-anonymizer` / `10.0.128.9` / 8093 | `vmss-anonymizer` | — |

Config details for the worker/anonymizer are in
[`apps/agent-guard/deploy/azure.md`](../../agent-guard/deploy/azure.md). Workers
carry **no** cache/embedder/Redis env (the cache lives in guardrails-service).

`SCANNER_API_URL` for guardrails = `http://10.0.128.61:8090`.

---

## 9. guardrails-service — VMSS, then Application Gateway (public)

### 9a. VMSS (no LB yet — App Gateway is the frontend)

Portal → **Virtual machine scale sets** → **Create**
- **Basics:** Name `vmss-guardrails` · **Uniform** · Ubuntu 22.04 · Size
  **Standard_D4s_v5** · Zones **1, 2, 3** · Instances `2`
- **Networking:** VNet/subnet `snet-services` · no public IP · **no load balancer** for now
- **Management → Identity → System assigned = On**
- **Scaling:** Custom → CPU > `65` (2 min) scale out by 2; CPU < `30` (5 min)
  scale in by 1; Min `2` · Max `20`
- **Advanced → Custom data** → paste the **guardrails** cloud-init (sets
  `CACHE_MODE=decide`, `EMBEDDER_URL=http://10.0.128.13:8094`,
  `REDIS_URL=redis://:<pass>@10.0.128.71:6379`, `SCANNER_API_URL=http://10.0.128.61:8090`,
  `CACHE_BLOCK_DISTANCE_THRESHOLD=0.0001`)
- **Create**, then grant its identity **Key Vault Secrets User** on `kv-guardrails`.

### 9b. Application Gateway (public HTTPS front)

Portal → **Application gateways** → **Create**
- **Basics:** Name `agw-guardrails` · Region · **Tier: Standard v2** ·
  **Autoscaling: Enabled** (min 2) · VNet `vnet-guardrails` · Subnet `snet-appgw`
- **Frontends:** Frontend IP type **Public** → **Add new** public IP `pip-guardrails`
- **Backends:** **Add a backend pool** `bepool-guardrails` → **Targets: VMSS** →
  select `vmss-guardrails`
- **Configuration** (routing rule) → **+ Add a routing rule**:
  - **Listener:** name `https-listener` · Frontend **Public** · Protocol **HTTPS** ·
    Port `443` · upload your **PFX certificate** + password
  - **Backend targets:** target `bepool-guardrails` · **Add new HTTP setting**:
    protocol **HTTP**, port **8080**, name `bes-8080`
    - (Optional health probe → path `/health` or the validate endpoint, port 8080)
- **Review + create** → **Create**

The Application Gateway's public IP is your entry point:
`https://<pip-guardrails>/api/validate/request`.

---

## 10. Verify

- **App Gateway** → **Backend health** → `bepool-guardrails` should show **Healthy**.
- POST a prompt twice (identical) to `https://<public-ip>/api/validate/request`
  with an `Authorization: Bearer <jwt>` header.
- On a `vmss-guardrails` instance (SSH via bastion/jump): `docker logs <container>
  | grep "Served from semantic cache"` — the 2nd call should log
  `match=exact`.
- On `agent-guard-redis`: `docker exec <redis> redis-cli -a <pass> FT._LIST` →
  `guardrails_shadow_cache`.

> Multi-tenant isolation is automatic — the cache key includes the JWT
> `accountId`. Just keep JWT auth enabled on guardrails-service.

---

## 11. Updates

To roll a new image (Portal):
- **VMSS** → **Instances** → select all → **Upgrade**, or
- **VMSS** → **Instances** → **Reimage** (re-runs cloud-init, re-pulls `:latest`).
- Redis: **VM** → **Restart** (empty cache re-warms on its own).

Keep the working image tag in cloud-init so a reimage is also your rollback.
Set the VMSS **Upgrade policy** to **Rolling** and keep ≥1 healthy instance per
zone during a roll (the Azure analogue of GCP's `maxSurge` zone rule).
