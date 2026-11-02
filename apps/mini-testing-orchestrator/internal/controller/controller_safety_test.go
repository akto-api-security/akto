package controller

// Safety and visibility tests: broken, not-ready and unreachable pods, the
// replica clamp, old clusters, hold reporting, heartbeat snapshots, the
// stabilization window and deletion-cost cleanup. Helpers are in
// controller_test.go.

import (
	"bytes"
	"context"
	"errors"
	"io"
	"strings"
	"testing"
	"time"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"

	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/probe"
)

func TestBrokenPodIsRemovedAndIdleKeptAsMin(t *testing.T) {
	// One pod has been not ready for 15m (crashlooping); with nothing busy we
	// drop to min, and the ReplicaSet removes the not-ready pod first.
	prober := &fakeProber{status: map[string]probe.Status{"10.0.0.1": {}}}
	c, client := newController([]runtime.Object{
		deployment(2),
		runningPod("idle", "10.0.0.1", time.Hour, nil),
		notReadyPod("broken", "10.0.0.2", 15*time.Minute),
	}, prober, 0)

	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	if got := replicas(t, client); got != 1 {
		t.Fatalf("replicas = %d, want 1", got)
	}
	if len(prober.drained) != 0 {
		t.Errorf("drained %v; the broken pod should go without draining the idle one", prober.drained)
	}
}

func TestFreshlyNotReadyPodHolds(t *testing.T) {
	prober := &fakeProber{status: map[string]probe.Status{"10.0.0.1": {}, "10.0.0.2": {}}}
	c, client := newController([]runtime.Object{
		deployment(3),
		runningPod("a", "10.0.0.1", time.Hour, nil),
		runningPod("b", "10.0.0.2", time.Hour, nil),
		notReadyPod("restarting", "10.0.0.3", time.Minute),
	}, prober, 0)

	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	if got := replicas(t, client); got != 3 {
		t.Fatalf("replicas = %d, want 3: the not-ready pod might be busy", got)
	}
}

func TestUnreachablePodIsKeptButDoesNotBlockScaleDown(t *testing.T) {
	prober := &fakeProber{
		status: map[string]probe.Status{"10.0.0.2": {}, "10.0.0.3": {}, "10.0.0.4": {}},
		errFor: map[string]error{"10.0.0.1": errors.New("timeout")},
	}
	c, client := newController([]runtime.Object{
		deployment(4),
		runningPod("unreachable", "10.0.0.1", 4*time.Hour, nil),
		runningPod("idle-1", "10.0.0.2", 3*time.Hour, nil),
		runningPod("idle-2", "10.0.0.3", 2*time.Hour, nil),
		runningPod("idle-3", "10.0.0.4", time.Hour, nil),
	}, prober, 0)

	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	// unreachable counts as maybe busy: keep it plus one spare
	if got := replicas(t, client); got != 2 {
		t.Fatalf("replicas = %d, want 2", got)
	}
	if got := annotation(t, client, "unreachable"); got != "" {
		t.Errorf("unreachable pod marked for deletion: %q", got)
	}
}

func TestDrainedPodIsRedrainedToRenewIt(t *testing.T) {
	prober := &fakeProber{status: map[string]probe.Status{"10.0.0.1": {Draining: true}, "10.0.0.2": {}}}
	c, client := newController([]runtime.Object{
		deployment(2),
		runningPod("drained", "10.0.0.1", time.Hour, map[string]string{DeletionCostAnnotation: drainedDeletionCost}),
		runningPod("idle", "10.0.0.2", 2*time.Hour, nil),
	}, prober, 0)

	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	if len(prober.drained) != 1 || prober.drained[0] != "10.0.0.1" {
		t.Errorf("drained = %v, want the already-drained pod renewed", prober.drained)
	}
	if got := replicas(t, client); got != 1 {
		t.Fatalf("replicas = %d, want 1", got)
	}
}

func TestSetReplicasNeverBelowMinOrGrowsPastMax(t *testing.T) {
	var logs bytes.Buffer
	c, client := newControllerWith([]runtime.Object{deployment(2)}, &fakeProber{}, testOptions(0), &logs)
	dep, _ := client.AppsV1().Deployments(ns).Get(context.Background(), "mini-testing", metav1.GetOptions{})

	if err := c.setReplicas(context.Background(), dep, 2, 0, "test"); err != nil {
		t.Fatal(err)
	}
	if got := replicas(t, client); got != 1 {
		t.Fatalf("replicas = %d, want clamped to min 1", got)
	}
	dep, _ = client.AppsV1().Deployments(ns).Get(context.Background(), "mini-testing", metav1.GetOptions{})
	if err := c.setReplicas(context.Background(), dep, 1, 9, "test"); err != nil {
		t.Fatal(err)
	}
	if got := replicas(t, client); got != 5 {
		t.Fatalf("replicas = %d, want clamped to max 5", got)
	}
	if !strings.Contains(logs.String(), "clamped") {
		t.Error("clamp not logged")
	}
}

func TestScaleDownDisabledOnOldClusters(t *testing.T) {
	opts := testOptions(0)
	opts.ScaleDownEnabled = false
	prober := &fakeProber{status: map[string]probe.Status{"10.0.0.1": {}, "10.0.0.2": {}}}
	c, client := newControllerWith([]runtime.Object{
		deployment(2),
		runningPod("a", "10.0.0.1", time.Hour, nil),
		runningPod("b", "10.0.0.2", time.Hour, nil),
	}, prober, opts, io.Discard)

	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	if got := replicas(t, client); got != 2 {
		t.Fatalf("replicas = %d, want 2", got)
	}
	if len(prober.drained) != 0 {
		t.Errorf("drained %v with scale-down disabled", prober.drained)
	}
}

func TestLongHoldIsReportedOnceThenRecovery(t *testing.T) {
	var logs bytes.Buffer
	opts := testOptions(0)
	opts.StuckAfter = 10 * time.Minute
	prober := &fakeProber{status: map[string]probe.Status{"10.0.0.1": {Busy: true}}}
	dep := deployment(1)
	c, _ := newControllerWith([]runtime.Object{dep, runningPod("a", "10.0.0.1", time.Hour, nil)}, prober, opts, &logs)
	c.opts.Limits.Max = 1 // all busy at max: a hold
	now := time.Now()
	c.now = func() time.Time { return now }

	for i := 0; i < 3; i++ {
		if err := c.Reconcile(context.Background()); err != nil {
			t.Fatal(err)
		}
		now = now.Add(6 * time.Minute)
	}
	if n := strings.Count(logs.String(), "scaling held"); n != 1 {
		t.Fatalf("scaling held reported %d times, want 1:\n%s", n, logs.String())
	}
	if !strings.Contains(logs.String(), "raise testing.autoScaling.maxReplicas") {
		t.Error("hint missing")
	}

	prober.status["10.0.0.1"] = probe.Status{}
	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(logs.String(), "scaling resumed") {
		t.Error("recovery not reported")
	}
}

func TestPodErrorReportedOnceUntilItChanges(t *testing.T) {
	var logs bytes.Buffer
	prober := &fakeProber{errFor: map[string]error{"10.0.0.1": probe.ErrUnauthorized}}
	c, _ := newControllerWith([]runtime.Object{deployment(1), runningPod("a", "10.0.0.1", time.Hour, nil)},
		prober, testOptions(0), &logs)

	for i := 0; i < 3; i++ {
		_ = c.Reconcile(context.Background())
	}
	if n := strings.Count(logs.String(), "pod rejected the orchestrator token"); n != 1 {
		t.Fatalf("token error logged %d times, want 1", n)
	}
}

func TestSnapshotForHeartbeat(t *testing.T) {
	prober := &fakeProber{status: map[string]probe.Status{"10.0.0.1": {Busy: true}}}
	c, _ := newController([]runtime.Object{deployment(1), runningPod("a", "10.0.0.1", time.Hour, nil)}, prober, time.Minute)
	if got := c.Snapshot(); got["leader"] != false || got["code"] != nil {
		t.Fatalf("before leading: %v", got)
	}
	c.updateSnap(func(s *snapshot) { s.leader = true })
	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	got := c.Snapshot()
	pods := got["pods"].(map[string]int)
	if got["code"] != "scale_up" || got["replicas"] != 1 || pods["busy"] != 1 || got["lastScale"] != "1 -> 2" || got["maxReplicas"] != 5 {
		t.Errorf("snapshot = %v", got)
	}
}

// A pod that went idle partway through the window is not removed with the
// others: scale-down goes only to the highest count wanted over the window.
func TestPodIdleForLessThanDelayIsKept(t *testing.T) {
	prober := &fakeProber{status: map[string]probe.Status{"10.0.0.1": {Busy: true}, "10.0.0.2": {}, "10.0.0.3": {}}}
	c, client := newController([]runtime.Object{
		deployment(3),
		runningPod("late", "10.0.0.1", 3*time.Hour, nil),
		runningPod("idle-1", "10.0.0.2", 2*time.Hour, nil),
		runningPod("idle-2", "10.0.0.3", time.Hour, nil),
	}, prober, time.Minute)
	t0 := time.Now()
	at := func(d time.Duration) {
		t.Helper()
		c.now = func() time.Time { return t0.Add(d) }
		if err := c.Reconcile(context.Background()); err != nil {
			t.Fatal(err)
		}
	}

	at(0) // 1 busy + spare: wants 2
	prober.status["10.0.0.1"] = probe.Status{}
	at(50 * time.Second) // all idle: wants 1, but "late" has been idle only 10s
	if got := replicas(t, client); got != 3 {
		t.Fatalf("replicas = %d, want 3 before the window passes", got)
	}
	at(61 * time.Second)
	if got := replicas(t, client); got != 2 {
		t.Fatalf("replicas = %d, want 2: the window still holds the earlier 2", got)
	}

	// the ReplicaSet removes the drained pod; the remaining two are idle
	_ = client.CoreV1().Pods(ns).Delete(context.Background(), "idle-2", metav1.DeleteOptions{})
	dep, _ := client.AppsV1().Deployments(ns).Get(context.Background(), "mini-testing", metav1.GetOptions{})
	dep.Status.Replicas, dep.Status.UpdatedReplicas = 2, 2
	_, _ = client.AppsV1().Deployments(ns).UpdateStatus(context.Background(), dep, metav1.UpdateOptions{})

	at(100 * time.Second)
	if got := replicas(t, client); got != 2 {
		t.Fatalf("replicas = %d, want 2 until 1 has been wanted for a full minute", got)
	}
	at(115 * time.Second)
	if got := replicas(t, client); got != 1 {
		t.Fatalf("replicas = %d, want 1", got)
	}
}

// A pod still marked from an earlier tick that claims a run as its drain is
// renewed keeps the run: its deletion cost is cleared before replicas drop, so
// the ReplicaSet can only pick the pod drained this tick.
func TestOldDeletionCostClearedBeforeScaleDown(t *testing.T) {
	prober := &fakeProber{
		status:      map[string]probe.Status{"10.0.0.1": {Busy: true}, "10.0.0.2": {Draining: true}, "10.0.0.3": {}},
		busyOnDrain: map[string]bool{"10.0.0.2": true},
	}
	c, client := newController([]runtime.Object{
		deployment(3),
		runningPod("busy", "10.0.0.1", 3*time.Hour, nil),
		runningPod("was-drained", "10.0.0.2", 2*time.Hour, map[string]string{DeletionCostAnnotation: drainedDeletionCost}),
		runningPod("idle", "10.0.0.3", time.Hour, nil),
	}, prober, 0)

	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	if got := replicas(t, client); got != 2 {
		t.Fatalf("replicas = %d, want 2", got)
	}
	if got := annotation(t, client, "was-drained"); got != "" {
		t.Errorf("pod that claimed a run still marked for deletion: %q", got)
	}
	if got := annotation(t, client, "idle"); got != drainedDeletionCost {
		t.Errorf("idle pod deletion cost = %q, want %q", got, drainedDeletionCost)
	}
}
