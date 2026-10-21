// Package controller runs the reconcile loop that sizes the mini-testing
// Deployment.
//
// Scale-down never lets the ReplicaSet pick a busy pod: idle pods are drained
// first (they stop claiming test runs), marked with a low pod-deletion-cost,
// and only then is spec.replicas lowered. The ReplicaSet deletes pending pods,
// then not-ready pods, then the lowest deletion cost, so scale-down is held
// while a pod is freshly not ready, and pods not ready for longer than
// NotReadyGrace are counted as removable.
//
// Failures are safe by construction: replicas never drop below Min, a drain
// lapses on the pod unless renewed, and if this process stops the pods keep
// running and taking tests at their current count.
package controller

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"sort"
	"strings"
	"sync"
	"syscall"
	"time"

	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/client-go/kubernetes"

	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/probe"
	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/report"
	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/scaler"
)

const (
	DeletionCostAnnotation = "controller.kubernetes.io/pod-deletion-cost"
	drainedDeletionCost    = "-1000"

	// A pod refusing connections this soon after starting is still booting.
	startupQuiet = 2 * time.Minute
	// Repeats of the same reconcile error are logged at most this often.
	errorRepeatEvery = 5 * time.Minute
)

// Codes for holds decided here rather than in the scaler.
const (
	codeRollout           scaler.Code = "rollout"
	codeScaleDownDisabled scaler.Code = "scale_down_disabled"
	codeDrainFailed       scaler.Code = "drain_failed"
)

// holdHints says what to check when a hold lasts past StuckAfter.
var holdHints = map[scaler.Code]string{
	scaler.CodeNotReady:    "a pod is running but not ready; check `kubectl describe pod` and container restarts",
	scaler.CodeConverging:  "the pod count differs from spec.replicas; check for pods stuck terminating or pending on quota",
	scaler.CodeUnreachable: "pods are ready but /status fails; check ORCHESTRATOR_TOKEN matches (restart pods after the Secret changes) and that the mini-testing image has the orchestrator endpoints",
	scaler.CodeAtMax:       "every pod is busy at maxReplicas; raise testing.autoScaling.maxReplicas if test runs queue up",
	codeRollout:            "the Deployment rollout is not finishing; check `kubectl rollout status`",
	codeScaleDownDisabled:  "this cluster is older than Kubernetes 1.22 and ignores pod-deletion-cost, so scale-down could remove a busy pod; it stays off",
	codeDrainFailed:        "idle pods could not be drained; check the orchestrator can reach pods on the metrics port",
}

type Options struct {
	Namespace      string
	Deployment     string
	Limits         scaler.Limits
	ScaleDownDelay time.Duration
	PollInterval   time.Duration
	// NotReadyGrace is how long a pod may be not ready before it counts as
	// broken and removable.
	NotReadyGrace time.Duration
	// StuckAfter is how long a hold may last before it is reported.
	StuckAfter time.Duration
	// SummaryEvery is how often the current state is reported.
	SummaryEvery time.Duration
	// ScaleDownEnabled is false on clusters that ignore pod-deletion-cost.
	ScaleDownEnabled bool
}

type Controller struct {
	client kubernetes.Interface
	prober probe.Prober
	opts   Options
	log    *slog.Logger
	events *report.Reporter
	now    func() time.Time

	// recent holds the replica count each tick wanted, over the last
	// ScaleDownDelay plus the newest sample before it. Scale-down goes only
	// to the highest of them, as the HPA's stabilization window does, so a
	// pod must be spare for the whole delay before it is removed.
	recent []desiredSample

	lastCode    scaler.Code
	holdCode    scaler.Code
	holdSince   time.Time
	stuckSent   bool
	lastSummary time.Time
	podErrors   map[string]string
	lastErr     string
	lastErrAt   time.Time

	// snap is read by the heartbeat goroutine.
	snapMu sync.Mutex
	snap   snapshot
}

type desiredSample struct {
	at      time.Time
	desired int
}

// recordDesired adds this tick's desired count and drops samples older than
// the window, keeping the newest one before it so the window's start is known.
func (c *Controller) recordDesired(now time.Time, desired int) {
	c.recent = append(c.recent, desiredSample{at: now, desired: desired})
	cutoff := now.Add(-c.opts.ScaleDownDelay)
	keep := 0
	for i, s := range c.recent {
		if s.at.After(cutoff) {
			break
		}
		keep = i
	}
	c.recent = c.recent[keep:]
}

// stableDesired is the highest count wanted over the window, and whether the
// window has been observed in full.
func (c *Controller) stableDesired(now time.Time) (int, bool) {
	if len(c.recent) == 0 {
		return 0, false
	}
	highest := 0
	for _, s := range c.recent {
		highest = max(highest, s.desired)
	}
	return highest, !c.recent[0].at.After(now.Add(-c.opts.ScaleDownDelay))
}

type snapshot struct {
	leader      bool
	obs         scaler.Observed
	code        scaler.Code
	reason      string
	observedAt  time.Time
	lastScale   string
	lastScaleAt time.Time
}

// Snapshot is the current state, for heartbeats.
func (c *Controller) Snapshot() map[string]any {
	c.snapMu.Lock()
	s := c.snap
	c.snapMu.Unlock()
	out := map[string]any{
		"leader":           s.leader,
		"namespace":        c.opts.Namespace,
		"deployment":       c.opts.Deployment,
		"minReplicas":      c.opts.Limits.Min,
		"maxReplicas":      c.opts.Limits.Max,
		"scaleDownEnabled": c.opts.ScaleDownEnabled,
	}
	if !s.leader {
		return out
	}
	out["code"] = string(s.code)
	out["reason"] = s.reason
	out["observedAt"] = s.observedAt.Unix()
	out["replicas"] = s.obs.Current
	out["pods"] = map[string]int{
		"busy": s.obs.Busy, "idle": s.obs.Idle, "draining": s.obs.Draining, "pending": s.obs.Pending,
		"notReady": s.obs.NotReady, "broken": s.obs.Broken, "unreachable": s.obs.Unreachable,
	}
	if s.lastScale != "" {
		out["lastScale"] = s.lastScale
		out["lastScaleAt"] = s.lastScaleAt.Unix()
	}
	return out
}

func (c *Controller) updateSnap(f func(*snapshot)) {
	c.snapMu.Lock()
	f(&c.snap)
	c.snapMu.Unlock()
}

func New(client kubernetes.Interface, prober probe.Prober, opts Options, log *slog.Logger, events *report.Reporter) *Controller {
	return &Controller{
		client: client, prober: prober, opts: opts, log: log, events: events, now: time.Now,
		podErrors: map[string]string{},
	}
}

// Run reconciles every PollInterval until ctx is cancelled.
func (c *Controller) Run(ctx context.Context) {
	c.updateSnap(func(s *snapshot) { s.leader = true })
	defer c.updateSnap(func(s *snapshot) { s.leader = false })
	ticker := time.NewTicker(c.opts.PollInterval)
	defer ticker.Stop()
	for {
		rctx, cancel := context.WithTimeout(ctx, c.opts.PollInterval)
		c.noteReconcileError(c.Reconcile(rctx))
		cancel()
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

// noteReconcileError reports a failed reconcile once, then repeats it only
// every errorRepeatEvery, and reports the recovery.
func (c *Controller) noteReconcileError(err error) {
	if err == nil {
		if c.lastErr != "" {
			c.events.Info("reconcile recovered", "after", c.lastErr)
			c.lastErr = ""
		}
		return
	}
	msg := err.Error()
	if msg == c.lastErr && c.now().Sub(c.lastErrAt) < errorRepeatEvery {
		c.log.Debug("reconcile failed", "err", msg)
		return
	}
	c.lastErr, c.lastErrAt = msg, c.now()
	hint := "scaling is paused until this clears; pods keep running at the current replica count"
	if apierrors.IsForbidden(err) {
		hint = "the orchestrator's Role or RoleBinding is missing permissions; " + hint
	}
	c.events.Error("reconcile failed", "err", msg, "hint", hint)
}

type podState int

const (
	stateBusy podState = iota
	stateIdle
	stateDraining
	statePending
	stateNotReady
	stateBroken
	stateUnreachable
)

type observedPod struct {
	pod   *corev1.Pod
	state podState
}

func (c *Controller) Reconcile(ctx context.Context) error {
	dep, err := c.client.AppsV1().Deployments(c.opts.Namespace).Get(ctx, c.opts.Deployment, metav1.GetOptions{})
	if err != nil {
		return fmt.Errorf("get deployment %s/%s: %w", c.opts.Namespace, c.opts.Deployment, err)
	}
	current := int32(1)
	if dep.Spec.Replicas != nil {
		current = *dep.Spec.Replicas
	}
	if reason := rolloutInProgress(dep, current); reason != "" {
		c.recordDesired(c.now(), int(current))
		c.noteDecision(codeRollout, reason, scaler.Observed{Current: int(current)})
		return nil
	}

	pods, err := c.observe(ctx, dep)
	if err != nil {
		return err
	}
	obs := scaler.Observed{Current: int(current)}
	for _, p := range pods {
		switch p.state {
		case stateBusy:
			obs.Busy++
		case stateIdle:
			obs.Idle++
		case stateDraining:
			obs.Draining++
		case statePending:
			obs.Pending++
		case stateNotReady:
			obs.NotReady++
		case stateBroken:
			obs.Broken++
		case stateUnreachable:
			obs.Unreachable++
		}
	}

	d := scaler.Decide(obs, c.opts.Limits)
	if d.ScaleDown(obs.Current) && !c.opts.ScaleDownEnabled {
		d = scaler.Decision{Desired: obs.Current, Code: codeScaleDownDisabled, Reason: "scale-down disabled: " + d.Reason}
	}
	c.noteDecision(d.Code, d.Reason, obs)
	log := c.log.With(stateAttrs(obs)...)

	now := c.now()
	c.recordDesired(now, d.Desired)
	switch {
	case d.Undrain > 0:
		c.undrain(ctx, pods, d.Undrain, d.Reason)
		return nil
	case d.ScaleUp(obs.Current):
		return c.setReplicas(ctx, dep, current, int32(d.Desired), d.Reason)
	case d.ScaleDown(obs.Current):
		stable, full := c.stableDesired(now)
		if !full || stable >= obs.Current {
			log.Debug("scale down pending delay", "desired", d.Desired, "highest_in_window", stable,
				"window", c.opts.ScaleDownDelay.String(), "reason", d.Reason)
			return nil
		}
		// stable is at least d.Desired, so it removes no more than Decide allowed.
		d.Desired = stable
		return c.scaleDown(ctx, dep, pods, obs, d)
	default:
		log.Debug("holding", "code", d.Code, "reason", d.Reason)
		return nil
	}
}

// noteDecision logs when the decision code changes, reports holds that last
// past StuckAfter (and their end), and reports a summary every SummaryEvery.
func (c *Controller) noteDecision(code scaler.Code, reason string, obs scaler.Observed) {
	now := c.now()
	c.updateSnap(func(s *snapshot) { s.obs, s.code, s.reason, s.observedAt = obs, code, reason, now })
	if code != c.lastCode {
		c.log.Info("decision changed", append([]any{"from", c.lastCode, "to", code, "reason", reason}, stateAttrs(obs)...)...)
		c.lastCode = code
	}

	if _, isHold := holdHints[code]; isHold {
		if code != c.holdCode {
			c.holdCode, c.holdSince, c.stuckSent = code, now, false
		}
		if held := now.Sub(c.holdSince); !c.stuckSent && held >= c.opts.StuckAfter {
			c.events.Warn("scaling held", append([]any{"code", code, "for", held.Round(time.Second).String(),
				"reason", reason, "hint", holdHints[code]}, stateAttrs(obs)...)...)
			c.stuckSent = true
		}
	} else if c.holdCode != "" {
		if c.stuckSent {
			c.events.Info("scaling resumed", "after", c.holdCode, "held_for", now.Sub(c.holdSince).Round(time.Second).String())
		}
		c.holdCode, c.stuckSent = "", false
	}

	if now.Sub(c.lastSummary) >= c.opts.SummaryEvery {
		c.lastSummary = now
		c.events.Info("status", append([]any{"code", code, "reason", reason}, stateAttrs(obs)...)...)
	}
}

func stateAttrs(o scaler.Observed) []any {
	return []any{"replicas", o.Current, "busy", o.Busy, "idle", o.Idle, "draining", o.Draining, "pending", o.Pending,
		"not_ready", o.NotReady, "broken", o.Broken, "unreachable", o.Unreachable}
}

// rolloutInProgress returns why scaling should wait for the Deployment, or "".
// Keel image updates roll the Deployment; pods of two ReplicaSets would muddle
// the counts.
func rolloutInProgress(dep *appsv1.Deployment, replicas int32) string {
	s := dep.Status
	switch {
	case s.ObservedGeneration < dep.Generation:
		return "deployment spec not yet observed"
	case s.UpdatedReplicas != replicas || s.Replicas != replicas:
		return fmt.Sprintf("rollout in progress (updated %d, total %d, want %d)", s.UpdatedReplicas, s.Replicas, replicas)
	}
	return ""
}

func (c *Controller) observe(ctx context.Context, dep *appsv1.Deployment) ([]observedPod, error) {
	sel, err := metav1.LabelSelectorAsSelector(dep.Spec.Selector)
	if err != nil {
		return nil, fmt.Errorf("deployment selector: %w", err)
	}
	list, err := c.client.CoreV1().Pods(c.opts.Namespace).List(ctx, metav1.ListOptions{LabelSelector: sel.String()})
	if err != nil {
		return nil, fmt.Errorf("list pods: %w", err)
	}

	var out []observedPod
	seen := map[string]bool{}
	for i := range list.Items {
		pod := &list.Items[i]
		// Terminating pods are already gone from the ReplicaSet's count, and
		// evicted or completed pods are never counted.
		if pod.DeletionTimestamp != nil || pod.Status.Phase == corev1.PodSucceeded || pod.Status.Phase == corev1.PodFailed {
			continue
		}
		seen[pod.Name] = true
		state := c.classify(ctx, pod)
		out = append(out, observedPod{pod: pod, state: state})

		// A deletion cost left on a pod that is no longer draining (its JVM
		// restarted, or an undrain failed) would make the ReplicaSet delete it
		// first, possibly mid-run.
		if state != stateDraining && pod.Annotations[DeletionCostAnnotation] == drainedDeletionCost {
			if err := c.setDeletionCost(ctx, pod, nil); err != nil {
				c.log.Warn("clearing stale deletion cost", "pod", pod.Name, "err", err)
			}
		}
	}
	for name := range c.podErrors {
		if !seen[name] {
			delete(c.podErrors, name)
		}
	}
	return out, nil
}

func (c *Controller) classify(ctx context.Context, pod *corev1.Pod) podState {
	if pod.Status.Phase == corev1.PodPending {
		return statePending
	}
	if pod.Status.Phase != corev1.PodRunning || !podReady(pod) || pod.Status.PodIP == "" {
		if c.now().Sub(notReadySince(pod)) >= c.opts.NotReadyGrace {
			return stateBroken
		}
		return stateNotReady
	}
	st, err := c.prober.Status(ctx, pod.Status.PodIP)
	if err != nil {
		c.notePodError(pod, err)
		return stateUnreachable
	}
	if prev, ok := c.podErrors[pod.Name]; ok {
		c.log.Info("pod status recovered", "pod", pod.Name, "after", prev)
		delete(c.podErrors, pod.Name)
	}
	switch {
	case st.Busy:
		return stateBusy
	case st.Draining:
		return stateDraining
	default:
		return stateIdle
	}
}

// notePodError reports a pod's /status error when it changes, not every tick.
func (c *Controller) notePodError(pod *corev1.Pod, err error) {
	msg := err.Error()
	if c.podErrors[pod.Name] == msg {
		return
	}
	c.podErrors[pod.Name] = msg
	switch {
	case errors.Is(err, probe.ErrUnauthorized):
		c.events.Error("pod rejected the orchestrator token", "pod", pod.Name,
			"hint", "ORCHESTRATOR_TOKEN differs between pods and orchestrator; restart both after the token Secret changes")
	case isConnRefused(err) && c.now().Sub(pod.CreationTimestamp.Time) < startupQuiet:
		c.log.Debug("pod status not up yet", "pod", pod.Name, "err", msg)
	default:
		c.events.Warn("pod status failed", "pod", pod.Name, "err", msg)
	}
}

func isConnRefused(err error) bool {
	var opErr *net.OpError
	return errors.Is(err, syscall.ECONNREFUSED) || (errors.As(err, &opErr) && strings.Contains(opErr.Error(), "connection refused"))
}

func podReady(pod *corev1.Pod) bool {
	for _, c := range pod.Status.Conditions {
		if c.Type == corev1.PodReady {
			return c.Status == corev1.ConditionTrue
		}
	}
	return false
}

// notReadySince is when the pod last stopped being ready, or when it was
// created if it never was.
func notReadySince(pod *corev1.Pod) time.Time {
	for _, c := range pod.Status.Conditions {
		if c.Type == corev1.PodReady && !c.LastTransitionTime.IsZero() {
			return c.LastTransitionTime.Time
		}
	}
	return pod.CreationTimestamp.Time
}

func (c *Controller) scaleDown(ctx context.Context, dep *appsv1.Deployment, pods []observedPod, obs scaler.Observed, d scaler.Decision) error {
	need := obs.Current - d.Desired
	// The ReplicaSet removes pending pods first, then not-ready (broken) ones,
	// then the lowest deletion cost.
	fromPending := min(obs.Pending, need)
	fromBroken := min(obs.Broken, need-fromPending)
	needDrained := need - fromPending - fromBroken

	// Already-drained pods first, then the newest idle pods.
	var candidates []observedPod
	for _, p := range pods {
		if p.state == stateDraining || p.state == stateIdle {
			candidates = append(candidates, p)
		}
	}
	sort.SliceStable(candidates, func(i, j int) bool {
		if (candidates[i].state == stateDraining) != (candidates[j].state == stateDraining) {
			return candidates[i].state == stateDraining
		}
		return candidates[j].pod.CreationTimestamp.Before(&candidates[i].pod.CreationTimestamp)
	})

	var removed []string
	var failures []string
	for _, p := range candidates {
		if len(removed) == needDrained {
			break
		}
		// Drain also renews an earlier drain before it lapses on the pod.
		st, err := c.prober.Drain(ctx, p.pod.Status.PodIP)
		if err != nil {
			failures = append(failures, fmt.Sprintf("%s: %v", p.pod.Name, err))
			continue
		}
		if st.Busy {
			// It claimed a run before the drain landed.
			c.log.Info("pod became busy while draining, keeping it", "pod", p.pod.Name)
			if _, err := c.prober.Undrain(ctx, p.pod.Status.PodIP); err != nil {
				c.log.Warn("undrain failed; the drain will lapse on the pod", "pod", p.pod.Name, "err", err)
			}
			continue
		}
		cost := drainedDeletionCost
		if err := c.setDeletionCost(ctx, p.pod, &cost); err != nil {
			failures = append(failures, fmt.Sprintf("%s: set deletion cost: %v", p.pod.Name, err))
			continue
		}
		removed = append(removed, p.pod.Name)
	}

	replicas := int32(obs.Current - fromPending - fromBroken - len(removed))
	if replicas == int32(obs.Current) {
		c.noteDecision(codeDrainFailed, fmt.Sprintf("wanted %d replicas but no pod could be drained", d.Desired), obs)
		c.events.Warn("scale down skipped: no pod could be drained", "desired", d.Desired, "failures", strings.Join(failures, "; "))
		return nil
	}
	if len(failures) > 0 {
		c.events.Warn("some pods could not be drained", "failures", strings.Join(failures, "; "))
	}

	// Only the pods drained above may carry the low deletion cost when replicas
	// drop. Any other pod still marked from an earlier tick (one that claimed a
	// run as its drain lapsed, or whose drain failed now) could otherwise be the
	// one the ReplicaSet deletes.
	keep := map[string]bool{}
	for _, name := range removed {
		keep[name] = true
	}
	for _, p := range pods {
		if keep[p.pod.Name] || p.pod.Annotations[DeletionCostAnnotation] != drainedDeletionCost {
			continue
		}
		if err := c.setDeletionCost(ctx, p.pod, nil); err != nil {
			c.events.Warn("scale down skipped: could not clear an old deletion cost", "pod", p.pod.Name, "err", err)
			return nil
		}
	}
	return c.setReplicas(ctx, dep, int32(obs.Current), replicas,
		fmt.Sprintf("%s; drained %v, pending removed %d, broken removed %d", d.Reason, removed, fromPending, fromBroken))
}

func (c *Controller) undrain(ctx context.Context, pods []observedPod, n int, reason string) {
	var undrained []string
	for _, p := range pods {
		if len(undrained) == n {
			break
		}
		if p.state != stateDraining {
			continue
		}
		if _, err := c.prober.Undrain(ctx, p.pod.Status.PodIP); err != nil {
			c.log.Warn("undrain failed; the drain will lapse on the pod", "pod", p.pod.Name, "err", err)
			continue
		}
		if err := c.setDeletionCost(ctx, p.pod, nil); err != nil {
			c.log.Warn("clearing deletion cost", "pod", p.pod.Name, "err", err)
		}
		undrained = append(undrained, p.pod.Name)
	}
	c.events.Info("undrained pods", "pods", undrained, "reason", reason)
}

// setDeletionCost sets the pod-deletion-cost annotation, or removes it when cost is nil.
func (c *Controller) setDeletionCost(ctx context.Context, pod *corev1.Pod, cost *string) error {
	patch, err := json.Marshal(map[string]any{
		"metadata": map[string]any{"annotations": map[string]any{DeletionCostAnnotation: cost}},
	})
	if err != nil {
		return err
	}
	_, err = c.client.CoreV1().Pods(c.opts.Namespace).Patch(ctx, pod.Name, types.MergePatchType, patch, metav1.PatchOptions{})
	return err
}

// setReplicas patches spec.replicas with the read resourceVersion as a
// precondition, so a concurrent change to the Deployment fails the patch
// rather than being overwritten. It is the only place replicas change, and it
// never goes below Min, nor grows past Max.
func (c *Controller) setReplicas(ctx context.Context, dep *appsv1.Deployment, current, replicas int32, reason string) error {
	if want := replicas; replicas < int32(c.opts.Limits.Min) || (replicas > current && replicas > int32(c.opts.Limits.Max)) {
		replicas = max(int32(c.opts.Limits.Min), min(replicas, max(current, int32(c.opts.Limits.Max))))
		c.events.Error("replica count out of bounds, clamped", "wanted", want, "clamped_to", replicas,
			"min", c.opts.Limits.Min, "max", c.opts.Limits.Max)
		if replicas == current {
			return nil
		}
	}
	patch, err := json.Marshal(map[string]any{
		"metadata": map[string]any{"resourceVersion": dep.ResourceVersion},
		"spec":     map[string]any{"replicas": replicas},
	})
	if err != nil {
		return err
	}
	_, err = c.client.AppsV1().Deployments(c.opts.Namespace).Patch(ctx, dep.Name, types.MergePatchType, patch, metav1.PatchOptions{})
	if err != nil {
		return fmt.Errorf("set replicas %d -> %d: %w", current, replicas, err)
	}
	verb := "scaled up"
	if replicas < current {
		verb = "scaled down"
	}
	c.updateSnap(func(s *snapshot) { s.lastScale, s.lastScaleAt = fmt.Sprintf("%d -> %d", current, replicas), c.now() })
	c.events.Info(verb, "from", current, "to", replicas, "reason", reason)
	return nil
}
