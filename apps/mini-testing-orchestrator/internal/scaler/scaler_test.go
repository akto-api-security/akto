package scaler

import "testing"

func TestDecide(t *testing.T) {
	limits := Limits{Min: 1, Max: 5, Step: 1}
	tests := []struct {
		name        string
		obs         Observed
		limits      Limits
		wantDesired int
		wantUndrain int
		wantCode    Code
	}{
		{"below min", Observed{Current: 0}, limits, 1, 0, CodeBelowMin},
		{"below min even with broken pods around", Observed{Current: 0, NotReady: 1}, limits, 1, 0, CodeBelowMin},
		{"single idle pod at min", Observed{Current: 1, Idle: 1}, limits, 1, 0, CodeSteady},
		{"single busy pod scales up", Observed{Current: 1, Busy: 1}, limits, 2, 0, CodeScaleUp},
		{"all busy uses step", Observed{Current: 2, Busy: 2}, Limits{Min: 1, Max: 10, Step: 3}, 5, 0, CodeScaleUp},
		{"step capped at max", Observed{Current: 4, Busy: 4}, Limits{Min: 1, Max: 5, Step: 3}, 5, 0, CodeScaleUp},
		{"all busy at max holds", Observed{Current: 5, Busy: 5}, limits, 5, 0, CodeAtMax},
		{"pending pod holds scale up", Observed{Current: 3, Busy: 2, Pending: 1}, limits, 3, 0, CodeSteady},
		{"not ready pod holds", Observed{Current: 3, Idle: 2, NotReady: 1}, limits, 3, 0, CodeNotReady},
		{"pods not converged holds", Observed{Current: 3, Busy: 1, Idle: 1}, limits, 3, 0, CodeConverging},
		{"idle pods scale down to busy plus spare", Observed{Current: 4, Busy: 1, Idle: 3}, limits, 2, 0, CodeScaleDown},
		{"all idle scale down to min", Observed{Current: 4, Idle: 4}, limits, 1, 0, CodeScaleDown},
		{"scale down respects min", Observed{Current: 3, Idle: 3}, Limits{Min: 2, Max: 5, Step: 1}, 2, 0, CodeScaleDown},
		{"keeps one spare while busy", Observed{Current: 2, Busy: 1, Idle: 1}, limits, 2, 0, CodeSteady},
		{"one idle spare among busy holds", Observed{Current: 3, Busy: 2, Idle: 1}, limits, 3, 0, CodeSteady},
		{"draining counts as spare", Observed{Current: 4, Busy: 1, Idle: 1, Draining: 2}, limits, 2, 0, CodeScaleDown},
		{"demand undrains instead of scaling", Observed{Current: 3, Busy: 2, Draining: 1}, limits, 3, 1, CodeUndrain},
		{"undrain uses step", Observed{Current: 4, Busy: 1, Draining: 3}, Limits{Min: 1, Max: 5, Step: 2}, 4, 2, CodeUndrain},
		{"all drained and none busy is not demand", Observed{Current: 2, Draining: 2}, limits, 1, 0, CodeScaleDown},
		{"idle with pending scales down to busy plus spare", Observed{Current: 4, Busy: 2, Idle: 1, Pending: 1}, limits, 3, 0, CodeScaleDown},
		{"spare capped at max", Observed{Current: 5, Busy: 5}, limits, 5, 0, CodeAtMax},
		{"above max drops idle pods", Observed{Current: 7, Busy: 4, Idle: 3}, limits, 5, 0, CodeScaleDown},
		{"above max sheds only idle pods", Observed{Current: 7, Busy: 6, Idle: 1}, limits, 6, 0, CodeScaleDown},
		{"above max and all busy holds", Observed{Current: 7, Busy: 7}, limits, 7, 0, CodeAtMax},

		// No leaks: a broken pod is removed, and does not block other scale-downs.
		{"broken pod alone is removed down to min", Observed{Current: 2, Idle: 1, Broken: 1}, limits, 1, 0, CodeScaleDown},
		{"broken pods go before idle ones count", Observed{Current: 4, Busy: 1, Idle: 1, Broken: 2}, limits, 2, 0, CodeScaleDown},
		{"broken pods don't count as capacity", Observed{Current: 3, Busy: 2, Broken: 1}, limits, 4, 0, CodeScaleUp},
		{"only broken pods keeps min", Observed{Current: 1, Broken: 1}, limits, 1, 0, CodeSteady},

		// Unreachable pods: treated as maybe busy, never removed, never a scale-up signal.
		{"unreachable blocks scale up", Observed{Current: 2, Busy: 1, Unreachable: 1}, limits, 2, 0, CodeUnreachable},
		{"all unreachable never scales up", Observed{Current: 3, Unreachable: 3}, limits, 3, 0, CodeUnreachable},
		{"unreachable doesn't block removing idle pods", Observed{Current: 4, Unreachable: 1, Idle: 3}, limits, 2, 0, CodeScaleDown},
		{"unreachable counts as maybe busy for the spare", Observed{Current: 3, Busy: 1, Unreachable: 1, Idle: 1}, limits, 3, 0, CodeUnreachable},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := Decide(tt.obs, tt.limits)
			if got.Desired != tt.wantDesired || got.Undrain != tt.wantUndrain || got.Code != tt.wantCode {
				t.Errorf("Decide(%+v) = desired %d undrain %d code %s (%s), want desired %d undrain %d code %s",
					tt.obs, got.Desired, got.Undrain, got.Code, got.Reason, tt.wantDesired, tt.wantUndrain, tt.wantCode)
			}
		})
	}
}

// Decide must never ask for fewer than Min replicas or, when growing, more than Max.
func TestDecideBounds(t *testing.T) {
	l := Limits{Min: 2, Max: 4, Step: 3}
	for current := 0; current <= 6; current++ {
		for busy := 0; busy <= current; busy++ {
			for idle := 0; busy+idle <= current; idle++ {
				broken := current - busy - idle
				o := Observed{Current: current, Busy: busy, Idle: idle, Broken: broken}
				d := Decide(o, l)
				if d.Desired < l.Min {
					t.Fatalf("Decide(%+v) desired %d below min", o, d.Desired)
				}
				if d.Desired > current && d.Desired > l.Max {
					t.Fatalf("Decide(%+v) grew to %d above max", o, d.Desired)
				}
				if d.Desired < current && current-d.Desired > idle+broken {
					t.Fatalf("Decide(%+v) removes %d pods, only %d removable", o, current-d.Desired, idle+broken)
				}
			}
		}
	}
}
