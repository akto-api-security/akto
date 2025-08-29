package threat_detector

// DetectorResult is the unified return type for detectors.
type DetectorResult struct {
	isSuspicious bool
	Proof        *Proof
	Err          error
}

// IsSuspicious exposes the unexported isSuspicious flag to external packages.
func (r DetectorResult) IsSuspicious() bool { return r.isSuspicious }

// BaseDetector defines the common interface for all detection engines.
// Method names follow your requirement: detect() returning DetectorResult.
type BaseDetector interface {
	Name() string
	detect(text string) DetectorResult
}
