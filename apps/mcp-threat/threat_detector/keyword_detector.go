package threat_detector

import (
	"errors"
	"log"
	"regexp"
	"strings"
)

type KeywordDetector struct{}

type Proof struct {
	Keyword string `json:"keyword"`
	Snippet string `json:"snippet"`
}

//func (k KeywordDetector) Name() string { return "keyword" }

//var _ BaseDetector = KeywordDetector{}

var regexPattern = `(?i)(?:/etc/passwd[\W_]+|/etc/shadow[\W_]+|/etc/sudoers[\W_]+|/etc/hosts[\W_]+|/etc/crontab[\W_]+|/root/[\W_]+|~/\.ssh/[\W_]+|/proc/[\W_]+|/sys/[\W_]+|/dev/random[\W_]+|/dev/urandom[\W_]+|/\.env[\W_]+|\.env[\W_]+|/etc/environment[\W_]+|C:\\\\Windows\\\\System32[\W_]+|C:\\\\Users\\\\Administrator[\W_]+|%SYSTEMROOT%[\W_]+|id_rsa[\W_]+|id_ed25519[\W_]+|id_ecdsa[\W_]+|known_hosts[\W_]+|authorized_keys[\W_]+|GNU[\W_]+AFFERO[\W_]+GENERAL[\W_]+PUBLIC[\W_]+LICENSE[\W_]+Version[\W_]+3,[\W_]+19[\W_]+November[\W_]+2007|GNU[\W_]+GENERAL[\W_]+PUBLIC[\W_]+LICENSE[\W_]+Version[\W_]+3,[\W_]+29[\W_]+June[\W_]+2007|GNU[\W_]+LESSER[\W_]+GENERAL[\W_]+PUBLIC[\W_]+LICENSE[\W_]+Version[\W_]+3,[\W_]+29[\W_]+June[\W_]+2007|Mozilla[\W_]+Public[\W_]+License[\W_]+Version[\W_]+2\.0|Apache[\W_]+License[\W_]+Version[\W_]+2\.0,[\W_]+January[\W_]+2004|MIT[\W_]+License|Boost[\W_]+Software[\W_]+License[\W_]+-[\W_]+Version[\W_]+1\.0[\W_]+-[\W_]+August[\W_]+17th,[\W_]+2003|Copyright[\W_]+\(C\)|Copyright[\W_]+©|do[\W_]+not[\W_]+tell|do[\W_]+not[\W_]+inform|do[\W_]+not[\W_]+alert|do[\W_]+not[\W_]+notify|do[\W_]+not[\W_]+mention|hide[\W_]+this|user[\W_]+should[\W_]+not[\W_]+see|user[\W_]+must[\W_]+not[\W_]+see|user[\W_]+cannot[\W_]+see|don't[\W_]+show|don't[\W_]+display|don't[\W_]+reveal|<instructions>[\W_]+|<important>[\W_]+|<secret>[\W_]+|<system>[\W_]+|ignore[\W_]+previous[\W_]+instructions|ignore[\W_]+all[\W_]+instructions|ignore[\W_]+other[\W_]+instructions|ignore[\W_]+safety[\W_]+checks|ignore[\W_]+remaining[\W_]+instructions|ignore[\W_]+all|instead[\W_]+you[\W_]+should|always[\W_]+do|always[\W_]+include|always[\W_]+add|always[\W_]+insert|always[\W_]+append|when[\W_]+tool[\W_]+is[\W_]+available|when[\W_]+tool[\W_]+is[\W_]+present|override[\W_]+the[\W_]+behavior[\W_]+of|instead[\W_]+of[\W_]+using|modify[\W_]+the[\W_]+usage[\W_]+of|modify[\W_]+the[\W_]+behavior[\W_]+of|before[\W_]+using[\W_]+any[\W_]+tool|before[\W_]+using[\W_]+other[\W_]+tool|before[\W_]+using[\W_]+the[\W_]+tool|after[\W_]+using[\W_]+any[\W_]+tool|after[\W_]+using[\W_]+other[\W_]+tool|after[\W_]+using[\W_]+the[\W_]+tool|whenever[\W_]+you[\W_]+see|whenever[\W_]+you[\W_]+encounter|whenever[\W_]+you[\W_]+use|replace[\W_]+the[\W_]+function|replace[\W_]+the[\W_]+tool|replace[\W_]+the[\W_]+method|replace[\W_]+all[\W_]+function|replace[\W_]+all[\W_]+tool|replace[\W_]+all[\W_]+method|modify[\W_]+the[\W_]+agent|modify[\W_]+the[\W_]+model|modify[\W_]+the[\W_]+tool|modify[\W_]+the[\W_]+assistant|prioritize[\W_]+this|this[\W_]+is[\W_]+VERY[\W_]+important|this[\W_]+is[\W_]+VERY[\W_]+VERY|disregard[\W_]+prior[\W_]+instructions|forget[\W_]+prior[\W_]+instructions|bypass[\W_]+safety[\W_]+checks|disable[\W_]+safety[\W_]+checks|disable[\W_]+content[\W_]+filters|bypass[\W_]+content[\W_]+filters|ignore[\W_]+safety[\W_]+policies|ignore[\W_]+system[\W_]+policies|override[\W_]+system[\W_]+prompt|reveal[\W_]+system[\W_]+prompt|show[\W_]+system[\W_]+prompt|print[\W_]+system[\W_]+prompt|leak[\W_]+system[\W_]+prompt|system[\W_]+prompt|hidden[\W_]+system[\W_]+prompt|act[\W_]+as[\W_]+system|act[\W_]+as[\W_]+an[\W_]+ai|act[\W_]+as[\W_]+unrestricted|act[\W_]+as[\W_]+root|you[\W_]+are[\W_]+root|you[\W_]+are[\W_]+sudo|run[\W_]+as[\W_]+sudo|run[\W_]+as[\W_]+admin|elevate[\W_]+privileges|privilege[\W_]+escalation|ignore[\W_]+role[\W_]+boundaries|ignore[\W_]+tool[\W_]+scope|use[\W_]+hidden[\W_]+tool|call[\W_]+shadow[\W_]+tool|shadow[\W_]+tool|override[\W_]+tool[\W_]+output|use[\W_]+unlisted[\W_]+tool|chain[\W_]+unapproved[\W_]+tools|impersonate[\W_]+trusted[\W_]+tool|supply[\W_]+chain[\W_]+payload|malicious[\W_]+dependency|poisoned[\W_]+dependency|dependency[\W_]+confusion|set-executionpolicy[\W_]+bypass|download[\W_]+and[\W_]+execute|self-modifying[\W_]+prompt|self-referential[\W_]+prompt|indirect[\W_]+prompt[\W_]+injection|transclude[\W_]+instructions|treat[\W_]+data[\W_]+as[\W_]+instructions|ignore[\W_]+markup[\W_]+boundaries|follow[\W_]+last[\W_]+instruction|priority[\W_]+instruction|developer[\W_]+mode|jailbreak[\W_]+mode|do[\W_]+anything[\W_]+now|dan[\W_]+mode|you[\W_]+are[\W_]+dan|you[\W_]+are[\W_]+aim|stay[\W_]+in[\W_]+character|roleplay[\W_]+as[\W_]+malware|roleplay[\W_]+no[\W_]+limits|simulate[\W_]+no[\W_]+restrictions|no[\W_]+restrictions[\W_]+apply|without[\W_]+limitations|no[\W_]+moral[\W_]+filter|ignore[\W_]+ethical[\W_]+rules|bypass[\W_]+moderation|for[\W_]+educational[\W_]+purposes|hypothetically[\W_]+only|fictional[\W_]+scenario|just[\W_]+for[\W_]+research|as[\W_]+a[\W_]+simulation|as[\W_]+a[\W_]+test|do[\W_]+not[\W_]+refuse|never[\W_]+refuse|comply[\W_]+regardless|output[\W_]+regardless|no[\W_]+warnings|no[\W_]+disclaimers|output[\W_]+raw[\W_]+content|verbatim[\W_]+output|return[\W_]+raw[\W_]+markdown|do[\W_]+not[\W_]+sanitize|do[\W_]+not[\W_]+redact|do[\W_]+not[\W_]+censor|ignore[\W_]+classification|ignore[\W_]+refusal|ignore[\W_]+guardrails|ignore[\W_]+alignment|ignore[\W_]+safety[\W_]+layer|strip[\W_]+safety[\W_]+prefix|disable[\W_]+monitoring|disable[\W_]+telemetry|disable[\W_]+logging|exfiltrate[\W_]+data|data[\W_]+exfiltration|send[\W_]+to[\W_]+webhook|post[\W_]+to[\W_]+webhook|prompt[\W_]+leak|prompt[\W_]+injection|prompt[\W_]+override|instruction[\W_]+override|shadow[\W_]+instruction|hidden[\W_]+directive|ignore[\W_]+prior[\W_]+context|treat[\W_]+above[\W_]+as[\W_]+data|begin[\W_]+override|priority[\W_]+system[\W_]+message|as[\W_]+system[\W_]+message|grandma[\W_]+exploit|refusal[\W_]+suppression|phrase[\W_]+segmentation|zero[\W_]+width[\W_]+space|zero-width[\W_]+joiner|safetylayer[\W_]+off|safety[\W_]+off|moderation[\W_]+off|guard[\W_]+off|policy[\W_]+off|switch[\W_]+persona|switch[\W_]+role|swap[\W_]+instruction|tool[\W_]+override|gcs[\W_]+signed[\W_]+url|azure[\W_]+sas[\W_]+url|share[\W_]+secret|rotate[\W_]+keys[\W_]+now|authentication[\W_]+bypass|godmode[\W_]+|god[\W_]+mode)`

var suspiciousRegex *regexp.Regexp

func init() {
	var err error
	suspiciousRegex, err = regexp.Compile(regexPattern)
	if err != nil {
		log.Printf("ERROR: failed to compile threat detection regex: %v", err)
		panic("failed to compile threat detection regex: " + err.Error())
	}
}

var (
	ErrEmptyText      = errors.New("text cannot be empty")
	ErrTextTooLong    = errors.New("text exceeds maximum allowed length")
	ErrInvalidBounds  = errors.New("invalid regex match bounds")
	ErrInvalidSnippet = errors.New("failed to extract valid snippet")
)

const (
	maxTextLength = 10 * 1024 * 1024 // 10MB limit
	contextBytes  = 20
)

func (k KeywordDetector) Detect(text string) DetectorResult {
	if err := validateText(text); err != nil {
		log.Printf("ERROR: KeywordDetector text validation failed: %v", err)
		return DetectorResult{isSuspicious: false, Proof: nil, Err: err}
	}

	found, proofPtr, err := containsKeywords(text)
	if err != nil {
		log.Printf("ERROR: KeywordDetector keyword detection failed: %v", err)
		return DetectorResult{isSuspicious: false, Proof: nil, Err: err}
	}
	if !found {
		return DetectorResult{isSuspicious: false, Proof: nil, Err: nil}
	}
	return DetectorResult{isSuspicious: true, Proof: proofPtr, Err: nil}
}

func validateText(text string) error {
	if strings.TrimSpace(text) == "" {
		return ErrEmptyText
	}
	if len(text) > maxTextLength {
		return ErrTextTooLong
	}
	return nil
}

func containsKeywords(text string) (bool, *Proof, error) {
	if !suspiciousRegex.MatchString(text) {
		return false, nil, nil
	}

	loc := suspiciousRegex.FindStringIndex(text)
	start, end := loc[0], loc[1]

	proof := &Proof{
		Keyword: text[start:end],
		Snippet: text[start:end],
	}

	if err := validateMatchBounds(start, end, len(text)); err != nil {
		log.Printf("WARNING: KeywordDetector invalid match bounds, using raw match: %v", err)
		return true, proof, nil
	}

	snippet, err := extractSnippet(text, start, end, contextBytes)
	if err != nil {
		log.Printf("WARNING: KeywordDetector snippet extraction failed, using raw match: %v", err)
		return true, proof, nil
	}

	proof.Snippet = snippet

	return true, proof, nil
}

func validateMatchBounds(start, end, textLen int) error {
	if start < 0 || end < 0 {
		return ErrInvalidBounds
	}
	if start >= textLen || end > textLen {
		return ErrInvalidBounds
	}
	if start >= end {
		return ErrInvalidBounds
	}
	return nil
}

func extractSnippet(text string, start, end, context int) (string, error) {
	if context < 0 {
		err := errors.New("context size cannot be negative")
		log.Printf("ERROR: KeywordDetector invalid context size: %v", err)
		return "", err
	}

	before := start - context
	if before < 0 {
		before = 0
	}

	after := end + context
	if after > len(text) {
		after = len(text)
	}

	if before >= after {
		log.Printf("ERROR: KeywordDetector invalid snippet bounds: before=%d, after=%d", before, after)
		return "", ErrInvalidSnippet
	}

	snippet := text[before:after]

	if strings.TrimSpace(snippet) == "" {
		log.Printf("ERROR: KeywordDetector empty snippet extracted: before=%d, after=%d", before, after)
		return "", ErrInvalidSnippet
	}

	return snippet, nil
}
