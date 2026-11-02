// Command orchestrator autoscales the Akto mini-testing Deployment without
// Prometheus or KEDA. See README.md.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/clientcmd"
	"k8s.io/client-go/tools/leaderelection"
	"k8s.io/client-go/tools/leaderelection/resourcelock"

	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/akto"
	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/config"
	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/controller"
	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/heartbeat"
	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/probe"
	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/report"
	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/scaler"
)

// pod-deletion-cost is honoured from Kubernetes 1.22 (beta, on by default).
const minDeletionCostMinor = 22

// version is set at build time: -ldflags "-X main.version=..."
var version = "dev"

// The orchestrator runs with the mini-testing module's token, so it exchanges
// a scoped token for that module type.
const tokenModuleType = "MINI_TESTING"

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: logLevel()}))

	cfg, err := config.Load()
	if err != nil {
		log.Error("invalid config", "err", err)
		os.Exit(1)
	}
	restCfg, err := kubeConfig()
	if err != nil {
		log.Error("kubernetes config", "err", err)
		os.Exit(1)
	}
	client, err := kubernetes.NewForConfig(restCfg)
	if err != nil {
		log.Error("kubernetes client", "err", err)
		os.Exit(1)
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	var cyborg *akto.Client
	if cfg.ReportToAkto || cfg.Heartbeat {
		cyborg = akto.New(cfg.CyborgURL, cfg.CyborgToken, tokenModuleType, log)
		if cyborg == nil {
			log.Warn("not reporting to Akto: DATABASE_ABSTRACTOR_SERVICE_URL or _TOKEN is empty")
		}
	}
	reportOpts := report.Options{ModuleName: cfg.ModuleName, PerMinute: cfg.ReportPerMinute}
	if cfg.ReportToAkto {
		reportOpts.Client = cyborg
	}
	events := report.New(log, reportOpts)
	go events.Run(ctx)
	go serveHealth(ctx, cfg.HealthAddr, log)

	scaleDown := scaleDownSupported(client, events)
	ctrl := controller.New(client, probe.NewHTTPProber(cfg.PodStatusPort, cfg.ControlToken, cfg.PodStatusTimeout),
		controller.Options{
			Namespace:        cfg.Namespace,
			Deployment:       cfg.TargetDeployment,
			Limits:           scaler.Limits{Min: cfg.MinReplicas, Max: cfg.MaxReplicas, Step: cfg.ScaleUpStep},
			ScaleDownDelay:   cfg.ScaleDownDelay,
			PollInterval:     cfg.PollInterval,
			NotReadyGrace:    cfg.NotReadyGrace,
			StuckAfter:       cfg.StuckAfter,
			SummaryEvery:     cfg.SummaryEvery,
			ScaleDownEnabled: scaleDown,
		}, log, events)

	events.Info("starting", "version", version, "namespace", cfg.Namespace, "deployment", cfg.TargetDeployment,
		"min", cfg.MinReplicas, "max", cfg.MaxReplicas, "poll", cfg.PollInterval.String(),
		"scale_down_delay", cfg.ScaleDownDelay.String(), "scale_down", scaleDown,
		"leader_election", cfg.LeaderElection, "report_to_akto", reportOpts.Client != nil,
		"heartbeat", cfg.Heartbeat && cyborg != nil)
	// Every replica heartbeats; only the leader's carries scaling state.
	if cfg.Heartbeat && cyborg != nil {
		go heartbeat.New(cyborg, log, cfg.HeartbeatEvery, cfg.ModuleName, version, ctrl.Snapshot).Run(ctx)
	}

	if !cfg.LeaderElection {
		ctrl.Run(ctx)
		return
	}
	leaderelection.RunOrDie(ctx, leaderelection.LeaderElectionConfig{
		Lock: &resourcelock.LeaseLock{
			LeaseMeta:  metav1.ObjectMeta{Name: cfg.LeaseName, Namespace: cfg.Namespace},
			Client:     client.CoordinationV1(),
			LockConfig: resourcelock.ResourceLockConfig{Identity: cfg.PodName},
		},
		ReleaseOnCancel: true,
		LeaseDuration:   30 * time.Second,
		RenewDeadline:   20 * time.Second,
		RetryPeriod:     5 * time.Second,
		Callbacks: leaderelection.LeaderCallbacks{
			OnStartedLeading: func(ctx context.Context) {
				events.Info("became leader", "identity", cfg.PodName)
				ctrl.Run(ctx)
			},
			OnStoppedLeading: func() {
				events.Info("lost leadership", "identity", cfg.PodName)
			},
		},
	})
}

// scaleDownSupported reports whether the cluster honours pod-deletion-cost.
// Without it the ReplicaSet could remove a busy pod, so scale-down stays off;
// scale-up and min replicas still work. An unknown version is assumed fine,
// since every supported Kubernetes release is newer than 1.22.
func scaleDownSupported(client kubernetes.Interface, events *report.Reporter) bool {
	v, err := client.Discovery().ServerVersion()
	if err != nil {
		events.Warn("could not read the Kubernetes version; assuming pod-deletion-cost is supported", "err", err)
		return true
	}
	minor, err := strconv.Atoi(strings.TrimRight(v.Minor, "+"))
	if err != nil || v.Major != "1" {
		events.Warn("unrecognised Kubernetes version; assuming pod-deletion-cost is supported", "major", v.Major, "minor", v.Minor)
		return true
	}
	if minor < minDeletionCostMinor {
		events.Error("Kubernetes is older than 1.22: scale-down is disabled so no busy pod can be removed",
			"version", v.GitVersion)
		return false
	}
	return true
}

// kubeConfig uses the in-cluster service account, falling back to KUBECONFIG
// or ~/.kube/config for local runs.
func kubeConfig() (*rest.Config, error) {
	if cfg, err := rest.InClusterConfig(); err == nil {
		return cfg, nil
	}
	rules := clientcmd.NewDefaultClientConfigLoadingRules()
	return clientcmd.NewNonInteractiveDeferredLoadingClientConfig(rules, nil).ClientConfig()
}

func serveHealth(ctx context.Context, addr string, log *slog.Logger) {
	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})
	srv := &http.Server{Addr: addr, Handler: mux, ReadHeaderTimeout: 5 * time.Second}
	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = srv.Shutdown(shutdownCtx)
	}()
	if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Error("health server", "err", err)
	}
}

func logLevel() slog.Level {
	var l slog.Level
	if err := l.UnmarshalText([]byte(os.Getenv("LOG_LEVEL"))); err != nil {
		return slog.LevelInfo
	}
	return l
}
