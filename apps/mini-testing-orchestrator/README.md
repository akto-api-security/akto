# mini-testing-orchestrator

Autoscales the Akto mini-testing Deployment without Prometheus, KEDA, CRDs or
cluster-wide RBAC. It runs as one small Deployment in the same namespace and
needs only a namespaced Role. The `akto-mini-testing` helm chart deploys it
when `testing.autoScaling.mode=orchestrator`.

## How it works

Each mini-testing pod runs one test run at a time. When `AUTO_SCALING_ENABLED`
and `ORCHESTRATOR_ENABLED` are set, the pod serves these endpoints on its
metrics port (9400) next to `/metrics` (`OrchestratorControl` in [apps/mini-testing](../mini-testing/src/main/java/com/akto/testing/OrchestratorControl.java)):

| Endpoint | Effect |
|---|---|
| `GET /status` | `{"busy": bool, "draining": bool}`. `busy` means the pod holds a test run |
| `POST /drain` | stop claiming test runs; waits up to 2s for an in-flight poll, then replies whether the pod holds a run |
| `POST /undrain` | resume claiming |

Every request carries `X-Akto-Orchestrator-Token`. By default the token is derived on both sides from the database abstractor token they already receive, so the helm chart renders no random secret and GitOps tools such as Argo CD see a stable manifest. A drain lapses on the pod
after `ORCHESTRATOR_DRAIN_TTL_SECONDS` (default 120) unless renewed, so a pod
never stays out of service if the orchestrator dies mid-scale-down.

Every `POLL_INTERVAL` the leader:

1. Reads the Deployment, and holds while a rollout is in progress (for example
   a Keel image update).
2. Lists its pods and sorts them into:

   | State | Meaning | Scale-up | Scale-down |
   |---|---|---|---|
   | busy | holds a test run | signal | never removed |
   | idle | polling for work | blocks it | removable |
   | draining | idle, told not to claim | blocks it | removed first |
   | pending | not running yet | blocks it | removed first by the ReplicaSet |
   | not ready | running but not ready, for less than `NOT_READY_GRACE` | holds both | holds both |
   | broken | not ready for longer than `NOT_READY_GRACE` | not capacity | removable |
   | unreachable | ready, but `/status` fails | blocks it | never removed; counted as maybe busy |

3. Decides ([internal/scaler](internal/scaler/scaler.go)):
   - **Scale up** by `SCALE_UP_STEP` when every pod is busy. If some pods are
     draining, it undrains them instead.
   - **Scale down** to `max(MIN_REPLICAS, busy + unreachable + 1)`. While any
     pod may be busy, one idle pod is kept as a spare, the same headroom KEDA's
     0.9 busy target leaves. Scale-down uses a stabilization window like the
     HPA's: it goes only to the highest count wanted over the last
     `SCALE_DOWN_DELAY`, so every pod removed has been spare for that long.
   - **Hold** otherwise.
4. Scales down without touching busy pods. It drains the chosen idle pods
   (renewing earlier drains), drops any that report busy (they claimed a run
   first), sets `controller.kubernetes.io/pod-deletion-cost: -1000` on the rest,
   and only then lowers `spec.replicas`. The ReplicaSet deletes pending pods
   first, then not-ready pods, then the lowest deletion cost.

The scale-up signal is the same as the KEDA setup's: all pods busy. The
orchestrator doesn't see how many test runs are queued.

## Failure safety

- **Testing is never blocked.**
  - Replicas never go below `MIN_REPLICAS` (at least 1): every change passes
    through one clamp.
  - The chart starts the Deployment at 1 replica, even if the orchestrator
    never starts.
  - If the orchestrator stops, pods keep running and taking tests at their
    current count.
  - Drains lapse on the pod.
- **No leaked pods.**
  - A crashlooping pod counts as broken after `NOT_READY_GRACE` and is removed.
  - An unreachable pod never triggers scale-up, so a misconfiguration (such as
    a token mismatch) can't push replicas to max.
- **Old clusters.** Below Kubernetes 1.22 the ReplicaSet ignores
  pod-deletion-cost, so scale-down is turned off with an error, and scale-up
  still works.

## Visibility

- **stdout (JSON):**
  - One line per decision change.
  - A `status` summary every `SUMMARY_EVERY`.
  - Every scale and undrain.
  - Per-pod `/status` errors, logged when they change.
  - Reconcile errors, repeated at most every 5 minutes, with a hint when RBAC
    is the cause.
  - `LOG_LEVEL=DEBUG` adds every tick.
- **Akto (cyborg)**, when `REPORT_TO_AKTO` is on and the database abstractor
  URL and token are set: the important events are also sent to
  `/api/insertTestingLog`, the same way the module's own testing logs are,
  tagged `mini-testing-orchestrator : [<MINI_TESTING_NAME>]`. These events are:
  - `starting`
  - `scaled up` and `scaled down`
  - `undrained pods`
  - `scaling held`, sent when a hold lasts past `STUCK_AFTER`, with a hint
  - `scaling resumed`
  - the token, drain and reconcile errors
  - the periodic `status` summary

  Sending is async, capped by `REPORT_PER_MINUTE`, and never delays scaling. A
  failure is logged locally, at most every 5 minutes.

## Configuration

| Env | Default | |
|---|---|---|
| `NAMESPACE` | required | namespace of the Deployment |
| `TARGET_DEPLOYMENT` | required | mini-testing Deployment name |
| `MIN_REPLICAS` / `MAX_REPLICAS` | `1` / `10` | |
| `SCALE_UP_STEP` | `1` | pods added per poll while all are busy |
| `POLL_INTERVAL` | `15s` | |
| `SCALE_DOWN_DELAY` | `5m` | |
| `NOT_READY_GRACE` | `10m` | not ready longer than this counts as broken |
| `STUCK_AFTER` | `10m` | a hold longer than this is reported |
| `SUMMARY_EVERY` | `5m` | |
| `POD_STATUS_PORT` | `9400` | |
| `POD_STATUS_TIMEOUT` | `3s` | |
| `CONTROL_TOKEN` | derived | token for the pods' control endpoints; must match their `ORCHESTRATOR_TOKEN`. When empty, both sides derive the same token from `DATABASE_ABSTRACTOR_SERVICE_TOKEN` (HMAC-SHA256 of a fixed label), so no shared secret has to be generated or distributed |
| `LEADER_ELECTION` | `true` | Lease-based, so two replicas never act at once |
| `LEASE_NAME` | `mini-testing-orchestrator` | |
| `POD_NAME` | hostname | leader identity |
| `HEALTH_ADDR` | `:8080` | serves `/healthz` |
| `LOG_LEVEL` | `INFO` | `DEBUG` logs every tick |
| `REPORT_TO_AKTO` | `true` | send events to Akto |
| `DATABASE_ABSTRACTOR_SERVICE_URL` / `_TOKEN` | empty | same values as the mini-testing pods |
| `MINI_TESTING_NAME` | `mini-testing` | tags events |
| `REPORT_PER_MINUTE` | `30` | |

## RBAC

These permissions are needed in the target namespace only:

- `apps/deployments`: `get`, `patch` on the target Deployment
- `pods`: `get`, `list`, `patch` (the patch sets the deletion-cost annotation)
- `coordination.k8s.io/leases`: `create`, plus `get` and `update` on the lease

## Development

```bash
make test           # unit tests (scaler rules, controller against a fake clientset, reporter, heartbeat)
make build          # bin/orchestrator
make image TAG=dev  # local image for this machine's platform; nothing is pushed
NAMESPACE=akto TARGET_DEPLOYMENT=akto-mini-testing LEADER_ELECTION=false \
  ./bin/orchestrator   # runs against your current kubeconfig
```

When run outside the cluster, it uses `KUBECONFIG` or `~/.kube/config`. It also
needs network access to the pod IPs.

## Release

The image is built by the **Production** workflow (`.github/workflows/prod.yml`)
on `feature/mini-runtime-release`:

1. Run it with `release_version` set to the orchestrator version (e.g. `1.0.0`),
   and tick **Mini-Testing-Orchestrator**.
2. It pushes `public.ecr.aws/aktosecurity/akto-mini-testing-orchestrator:<release_version>`
   and `:latest` for linux/amd64 and linux/arm64.
3. Bump `testing.autoScaling.orchestrator.image.tag` in the `akto-mini-testing`
   helm chart to match.
