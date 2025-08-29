package threat_detector

// ChainDetector runs detectors in order and short-circuits on first hit (chain method only).
type ChainDetector struct {
	ordered []BaseDetector
}

func (c ChainDetector) Name() string { return "chain" }

func (c ChainDetector) detect(text string) DetectorResult {
	for _, d := range c.ordered {
		res := d.detect(text)
		if res.Err != nil {
			return res
		}
		if res.isSuspicious {
			return res
		}
	}
	return DetectorResult{}
}

// NewDefaultDetectorChain returns a chain that includes the KeywordDetector only.
func NewDefaultDetectorChain() BaseDetector {
	return ChainDetector{ordered: []BaseDetector{}}
}

// DetectText runs the default detector chain on the provided text and returns the result.
func DetectText(text string) DetectorResult {
	return NewDefaultDetectorChain().detect(text)
}
