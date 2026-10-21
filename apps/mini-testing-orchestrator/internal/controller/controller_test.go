package controller

import (
	"context"
	"io"
	"log/slog"
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
