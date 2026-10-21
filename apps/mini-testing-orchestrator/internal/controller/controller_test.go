package controller

import (
	"bytes"
	"context"
	"errors"
	"io"
	"log/slog"
	"strings"
	"testing"
	"time"

	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/client-go/kubernetes/fake"

	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/probe"
	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/report"
	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/scaler"
)

const ns = "akto"

type fakeProber struct {
	status map[string]probe.Status // by pod IP
	errFor map[string]error
	// busyOnDrain marks IPs that claim a run just as the drain lands.
	busyOnDrain map[string]bool
	drained     []string
	undrained   []string
}

func (f *fakeProber) Status(_ context.Context, ip string) (probe.Status, error) {
	if err := f.errFor[ip]; err != nil {
		return probe.Status{}, err
	}
	return f.status[ip], nil
}

func (f *fakeProber) Drain(_ context.Context, ip string) (probe.Status, error) {
	f.drained = append(f.drained, ip)
	st := f.status[ip]
	st.Draining = true
	st.Busy = f.busyOnDrain[ip]
	f.status[ip] = st
	return st, nil
}

func (f *fakeProber) Undrain(_ context.Context, ip string) (probe.Status, error) {
	f.undrained = append(f.undrained, ip)
	st := f.status[ip]
	st.Draining = false
	f.status[ip] = st
	return st, nil
}

func deployment(replicas int32) *appsv1.Deployment {
	return &appsv1.Deployment{
		ObjectMeta: metav1.ObjectMeta{Name: "mini-testing", Namespace: ns, Generation: 1, ResourceVersion: "1"},
		Spec: appsv1.DeploymentSpec{
			Replicas: &replicas,
			Selector: &metav1.LabelSelector{MatchLabels: map[string]string{"app": "mini-testing"}},
		},
		Status: appsv1.DeploymentStatus{ObservedGeneration: 1, Replicas: replicas, UpdatedReplicas: replicas},
	}
}

func runningPod(name, ip string, age time.Duration, annotations map[string]string) *corev1.Pod {
	return &corev1.Pod{
		ObjectMeta: metav1.ObjectMeta{
			Name: name, Namespace: ns, Labels: map[string]string{"app": "mini-testing"},
			Annotations:       annotations,
			CreationTimestamp: metav1.NewTime(time.Now().Add(-age)),
		},
		Status: corev1.PodStatus{
			Phase: corev1.PodRunning, PodIP: ip,
			Conditions: []corev1.PodCondition{{Type: corev1.PodReady, Status: corev1.ConditionTrue}},
		},
	}
}

func testOptions(delay time.Duration) Options {
	return Options{
		Namespace: ns, Deployment: "mini-testing",
		Limits:         scaler.Limits{Min: 1, Max: 5, Step: 1},
		ScaleDownDelay: delay, PollInterval: time.Second,
		NotReadyGrace: 10 * time.Minute, StuckAfter: 10 * time.Minute, SummaryEvery: time.Hour,
		ScaleDownEnabled: true,
	}
}

func newController(objs []runtime.Object, prober *fakeProber, delay time.Duration) (*Controller, *fake.Clientset) {
	return newControllerWith(objs, prober, testOptions(delay), io.Discard)
}

func newControllerWith(objs []runtime.Object, prober *fakeProber, opts Options, logOut io.Writer) (*Controller, *fake.Clientset) {
	client := fake.NewClientset(objs...)
	log := slog.New(slog.NewTextHandler(logOut, nil))
	c := New(client, prober, opts, log, report.New(log, report.Options{}))
	return c, client
}

func notReadyPod(name, ip string, notReadyFor time.Duration) *corev1.Pod {
	p := runningPod(name, ip, time.Hour, nil)
	p.Status.Conditions = []corev1.PodCondition{{
		Type: corev1.PodReady, Status: corev1.ConditionFalse,
		LastTransitionTime: metav1.NewTime(time.Now().Add(-notReadyFor)),
	}}
	return p
}

func replicas(t *testing.T, client *fake.Clientset) int32 {
	t.Helper()
	dep, err := client.AppsV1().Deployments(ns).Get(context.Background(), "mini-testing", metav1.GetOptions{})
	if err != nil {
		t.Fatal(err)
	}
	return *dep.Spec.Replicas
}

func annotation(t *testing.T, client *fake.Clientset, pod string) string {
	t.Helper()
	p, err := client.CoreV1().Pods(ns).Get(context.Background(), pod, metav1.GetOptions{})
	if err != nil {
		t.Fatal(err)
	}
	return p.Annotations[DeletionCostAnnotation]
}

func TestScaleUpWhenAllBusy(t *testing.T) {
	prober := &fakeProber{status: map[string]probe.Status{"10.0.0.1": {Busy: true}, "10.0.0.2": {Busy: true}}}
	c, client := newController([]runtime.Object{
		deployment(2),
		runningPod("a", "10.0.0.1", time.Hour, nil),
		runningPod("b", "10.0.0.2", time.Hour, nil),
	}, prober, time.Minute)

	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	if got := replicas(t, client); got != 3 {
		t.Fatalf("replicas = %d, want 3", got)
	}
}

func TestScaleDownDrainsIdlePodAfterDelay(t *testing.T) {
	prober := &fakeProber{status: map[string]probe.Status{"10.0.0.1": {Busy: true}, "10.0.0.2": {}, "10.0.0.3": {}}}
	c, client := newController([]runtime.Object{
		deployment(3),
		runningPod("busy", "10.0.0.1", 3*time.Hour, nil),
		runningPod("idle-old", "10.0.0.2", 2*time.Hour, nil),
		runningPod("idle-new", "10.0.0.3", time.Hour, nil),
	}, prober, time.Minute)
	now := time.Now()
	c.now = func() time.Time { return now }

	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	if got := replicas(t, client); got != 3 {
		t.Fatalf("scaled before delay: replicas = %d", got)
	}

	now = now.Add(2 * time.Minute)
	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	// Desired is busy + 1 spare = 2, so the newest idle pod goes.
	if got := replicas(t, client); got != 2 {
		t.Fatalf("replicas = %d, want 2", got)
	}
	if got := annotation(t, client, "idle-new"); got != drainedDeletionCost {
		t.Errorf("idle-new deletion cost = %q, want %q", got, drainedDeletionCost)
	}
	for _, p := range []string{"busy", "idle-old"} {
		if got := annotation(t, client, p); got != "" {
			t.Errorf("%s got deletion cost %q", p, got)
		}
	}
}

func TestPodClaimingRunDuringDrainIsKept(t *testing.T) {
	// Desired is busy + 1 spare = 2. The newest idle pod is tried first but
	// claims a run as the drain lands, so the older idle pod goes instead.
	prober := &fakeProber{
		status:      map[string]probe.Status{"10.0.0.1": {Busy: true}, "10.0.0.2": {}, "10.0.0.3": {}},
		busyOnDrain: map[string]bool{"10.0.0.3": true},
	}
	c, client := newController([]runtime.Object{
		deployment(3),
		runningPod("busy", "10.0.0.1", 3*time.Hour, nil),
		runningPod("idle", "10.0.0.2", 2*time.Hour, nil),
		runningPod("racing", "10.0.0.3", time.Hour, nil),
	}, prober, 0)

	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	if got := replicas(t, client); got != 2 {
		t.Fatalf("replicas = %d, want 2", got)
	}
	if len(prober.undrained) != 1 || prober.undrained[0] != "10.0.0.3" {
		t.Errorf("undrained = %v, want [10.0.0.3]", prober.undrained)
	}
	if got := annotation(t, client, "racing"); got != "" {
		t.Errorf("racing pod got deletion cost %q", got)
	}
	if got := annotation(t, client, "idle"); got != drainedDeletionCost {
		t.Errorf("idle pod deletion cost = %q, want %q", got, drainedDeletionCost)
	}
}

func TestDemandUndrainsBeforeScalingUp(t *testing.T) {
	prober := &fakeProber{status: map[string]probe.Status{"10.0.0.1": {Busy: true}, "10.0.0.2": {Draining: true}}}
	c, client := newController([]runtime.Object{
		deployment(2),
		runningPod("busy", "10.0.0.1", time.Hour, nil),
		runningPod("drained", "10.0.0.2", time.Hour, map[string]string{DeletionCostAnnotation: drainedDeletionCost}),
	}, prober, time.Minute)

	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	if got := replicas(t, client); got != 2 {
		t.Fatalf("replicas = %d, want 2", got)
	}
	if got := annotation(t, client, "drained"); got != "" {
		t.Errorf("deletion cost not cleared: %q", got)
	}
}

func TestHoldsDuringRollout(t *testing.T) {
	dep := deployment(2)
	dep.Status.UpdatedReplicas = 1
	prober := &fakeProber{status: map[string]probe.Status{"10.0.0.1": {Busy: true}, "10.0.0.2": {Busy: true}}}
	c, client := newController([]runtime.Object{
		dep,
		runningPod("a", "10.0.0.1", time.Hour, nil),
		runningPod("b", "10.0.0.2", time.Hour, nil),
	}, prober, 0)

	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	if got := replicas(t, client); got != 2 {
		t.Fatalf("replicas = %d, want 2", got)
	}
}

func TestClearsStaleDeletionCost(t *testing.T) {
	// The pod restarted its JVM, so it no longer reports draining but keeps the annotation.
	prober := &fakeProber{status: map[string]probe.Status{"10.0.0.1": {Busy: true}}}
	c, client := newController([]runtime.Object{
		deployment(1),
		runningPod("a", "10.0.0.1", time.Hour, map[string]string{DeletionCostAnnotation: drainedDeletionCost}),
	}, prober, 0)

	if err := c.Reconcile(context.Background()); err != nil {
		t.Fatal(err)
	}
	if got := annotation(t, client, "a"); got != "" {
		t.Errorf("deletion cost = %q, want cleared", got)
	}
}

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
