// Package scaler holds the replica decision. It is a pure function of the
// observed pod states so every rule can be table-tested.
package scaler

import "fmt"

// Observed is one snapshot of the target Deployment and its pods.
type Observed struct {
	Current  int // Deployment spec.replicas
	Busy     int // holding a test run
	Idle     int // polling for work
	Draining int // idle and told not to claim work
	Pending  int // not running yet (unscheduled, pulling images, ...)
	// NotReady pods are running but not ready, recently enough that they may
	// still hold a run. The ReplicaSet deletes not-ready pods before any
	// others, so scale-down waits for them.
	NotReady int
	// Broken pods have been not ready past the grace period. They are removed
	// first on scale-down, so one crashlooping pod can't hold extra pods up.
	Broken int
	// Unreachable pods are ready but /status failed (e.g. a token mismatch).
	// They might be busy, so they are never removed; and since nothing is
	// known about them, they block scale-up.
	Unreachable int
}

func (o Observed) total() int {
	return o.Busy + o.Idle + o.Draining + o.Pending + o.NotReady + o.Broken + o.Unreachable
}

type Limits struct {
	Min  int
	Max  int
	Step int
}

// Code identifies the rule behind a decision, stable across ticks, so callers
// can log changes and spot a hold that lasts too long.
type Code string

const (
	CodeBelowMin    Code = "below_min"
	CodeScaleUp     Code = "scale_up"
	CodeUndrain     Code = "undrain"
	CodeScaleDown   Code = "scale_down"
	CodeSteady      Code = "steady"
	CodeAtMax       Code = "at_max"
	CodeNotReady    Code = "not_ready"
	CodeConverging  Code = "converging"
	CodeUnreachable Code = "unreachable"
)

type Decision struct {
	Desired int
	// Undrain is how many draining pods to put back to work instead of
	// adding new ones.
	Undrain int
	Code    Code
	Reason  string
}

func (d Decision) ScaleUp(current int) bool   { return d.Desired > current }
func (d Decision) ScaleDown(current int) bool { return d.Desired < current }

// Decide has no view of the queue of pending test runs, so "every pod that
// can take work is busy" is the scale-up signal, as with the KEDA setup.
func Decide(o Observed, l Limits) Decision {
	hold := func(code Code, format string, args ...any) Decision {
		return Decision{Desired: o.Current, Code: code, Reason: fmt.Sprintf(format, args...)}
	}

	if o.Current < l.Min {
		return Decision{Desired: l.Min, Code: CodeBelowMin, Reason: fmt.Sprintf("below min replicas (%d < %d)", o.Current, l.Min)}
	}
	if total := o.total(); total != o.Current {
		return hold(CodeConverging, "waiting for pods to converge (%d pods, %d replicas)", total, o.Current)
	}
	if o.NotReady > 0 {
		return hold(CodeNotReady, "%d pod(s) not ready", o.NotReady)
	}

	// Busy is the only scale-up signal; a pod we can't read gives none.
	if o.Busy > 0 && o.Idle == 0 && o.Pending == 0 {
		if o.Unreachable > 0 {
			return hold(CodeUnreachable, "all readable pods busy, but %d pod(s) unreachable", o.Unreachable)
		}
		if o.Draining > 0 {
			n := min(o.Draining, l.Step)
			return Decision{Desired: o.Current, Undrain: n, Code: CodeUndrain, Reason: fmt.Sprintf("all %d active pods busy, undraining %d", o.Busy, n)}
		}
		if o.Current >= l.Max {
			return hold(CodeAtMax, "all %d pods busy, at max replicas (%d)", o.Busy, l.Max)
		}
		return Decision{Desired: min(l.Max, o.Current+l.Step), Code: CodeScaleUp, Reason: fmt.Sprintf("all %d pods busy", o.Busy)}
	}

	// While any pod may be busy, keep one idle spare so the next run starts at
	// once. Without it, a lone busy pod would have its spare removed and
	// re-added in a loop. KEDA's 0.9 busy target keeps the same headroom.
	want := l.Min
	if maybeBusy := o.Busy + o.Unreachable; maybeBusy > 0 {
		want = max(want, maybeBusy+1)
	}
	want = min(want, l.Max)
	// Only spare and broken pods can go, even when above max. Pending pods are
	// left out: they are capacity on its way.
	removable := o.Idle + o.Draining + o.Broken
	target := max(o.Current-removable, want)
	if target < o.Current {
		return Decision{Desired: target, Code: CodeScaleDown, Reason: fmt.Sprintf("%d idle and %d broken pod(s)", o.Idle+o.Draining, o.Broken)}
	}
	// Not steady while pods can't be read: scaling is partly blind, and a
	// lasting hold should be reported.
	if o.Unreachable > 0 {
		return hold(CodeUnreachable, "%d pod(s) unreachable (%d busy, %d idle)", o.Unreachable, o.Busy, o.Idle)
	}
	return hold(CodeSteady, "steady (%d busy, %d idle, %d pending)", o.Busy, o.Idle, o.Pending)
}
