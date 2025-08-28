package threat_detector

import (
	"regexp"
)

var regexPattern = `(?i)(?:/etc/passwd(?:[\s\S]*?)|/etc/shadow(?:[\s\S]*?)|/etc/sudoers(?:[\s\S]*?)|/etc/hosts(?:[\s\S]*?)|/etc/crontab(?:[\s\S]*?)|/root/(?:[\s\S]*?)|~/\.ssh/(?:[\s\S]*?)|/proc/(?:[\s\S]*?)|/sys/(?:[\s\S]*?)|/dev/random(?:[\s\S]*?)|/dev/urandom(?:[\s\S]*?)|/\.env(?:[\s\S]*?)|\.env(?:[\s\S]*?)|/etc/environment(?:[\s\S]*?)|C:\\\\Windows\\\\System32(?:[\s\S]*?)|C:\\\\Users\\\\Administrator(?:[\s\S]*?)|%SYSTEMROOT%(?:[\s\S]*?)|id_rsa(?:[\s\S]*?)|id_ed25519(?:[\s\S]*?)|id_ecdsa(?:[\s\S]*?)|known_hosts(?:[\s\S]*?)|authorized_keys(?:[\s\S]*?)|GNU(?:[\s\S]*?)AFFERO(?:[\s\S]*?)GENERAL(?:[\s\S]*?)PUBLIC(?:[\s\S]*?)LICENSE(?:[\s\S]*?)Version(?:[\s\S]*?)3,(?:[\s\S]*?)19(?:[\s\S]*?)November(?:[\s\S]*?)2007|GNU(?:[\s\S]*?)GENERAL(?:[\s\S]*?)PUBLIC(?:[\s\S]*?)LICENSE(?:[\s\S]*?)Version(?:[\s\S]*?)3,(?:[\s\S]*?)29(?:[\s\S]*?)June(?:[\s\S]*?)2007|GNU(?:[\s\S]*?)LESSER(?:[\s\S]*?)GENERAL(?:[\s\S]*?)PUBLIC(?:[\s\S]*?)LICENSE(?:[\s\S]*?)Version(?:[\s\S]*?)3,(?:[\s\S]*?)29(?:[\s\S]*?)June(?:[\s\S]*?)2007|Mozilla(?:[\s\S]*?)Public(?:[\s\S]*?)License(?:[\s\S]*?)Version(?:[\s\S]*?)2\.0|Apache(?:[\s\S]*?)License(?:[\s\S]*?)Version(?:[\s\S]*?)2\.0,(?:[\s\S]*?)January(?:[\s\S]*?)2004|MIT(?:[\s\S]*?)License|Boost(?:[\s\S]*?)Software(?:[\s\S]*?)License(?:[\s\S]*?)-(?:[\s\S]*?)Version(?:[\s\S]*?)1\.0(?:[\s\S]*?)-(?:[\s\S]*?)August(?:[\s\S]*?)17th,(?:[\s\S]*?)2003|Copyright(?:[\s\S]*?)\(C\)|Copyright(?:[\s\S]*?)©|do(?:[\s\S]*?)not(?:[\s\S]*?)tell|do(?:[\s\S]*?)not(?:[\s\S]*?)inform|do(?:[\s\S]*?)not(?:[\s\S]*?)alert|do(?:[\s\S]*?)not(?:[\s\S]*?)notify|do(?:[\s\S]*?)not(?:[\s\S]*?)mention|hide(?:[\s\S]*?)this|user(?:[\s\S]*?)should(?:[\s\S]*?)not(?:[\s\S]*?)see|user(?:[\s\S]*?)must(?:[\s\S]*?)not(?:[\s\S]*?)see|user(?:[\s\S]*?)cannot(?:[\s\S]*?)see|don't(?:[\s\S]*?)show|don't(?:[\s\S]*?)display|don't(?:[\s\S]*?)reveal|<instructions>(?:[\s\S]*?)|<important>(?:[\s\S]*?)|<secret>(?:[\s\S]*?)|<system>(?:[\s\S]*?)|ignore(?:[\s\S]*?)previous(?:[\s\S]*?)instructions|ignore(?:[\s\S]*?)all(?:[\s\S]*?)instructions|ignore(?:[\s\S]*?)other(?:[\s\S]*?)instructions|ignore(?:[\s\S]*?)safety(?:[\s\S]*?)checks|ignore(?:[\s\S]*?)remaining(?:[\s\S]*?)instructions|ignore(?:[\s\S]*?)all|instead(?:[\s\S]*?)you(?:[\s\S]*?)should|always(?:[\s\S]*?)do|always(?:[\s\S]*?)include|always(?:[\s\S]*?)add|always(?:[\s\S]*?)insert|always(?:[\s\S]*?)append|when(?:[\s\S]*?)tool(?:[\s\S]*?)is(?:[\s\S]*?)available|when(?:[\s\S]*?)tool(?:[\s\S]*?)is(?:[\s\S]*?)present|override(?:[\s\S]*?)the(?:[\s\S]*?)behavior(?:[\s\S]*?)of|instead(?:[\s\S]*?)of(?:[\s\S]*?)using|modify(?:[\s\S]*?)the(?:[\s\S]*?)usage(?:[\s\S]*?)of|modify(?:[\s\S]*?)the(?:[\s\S]*?)behavior(?:[\s\S]*?)of|before(?:[\s\S]*?)using(?:[\s\S]*?)any(?:[\s\S]*?)tool|before(?:[\s\S]*?)using(?:[\s\S]*?)other(?:[\s\S]*?)tool|before(?:[\s\S]*?)using(?:[\s\S]*?)the(?:[\s\S]*?)tool|after(?:[\s\S]*?)using(?:[\s\S]*?)any(?:[\s\S]*?)tool|after(?:[\s\S]*?)using(?:[\s\S]*?)other(?:[\s\S]*?)tool|after(?:[\s\S]*?)using(?:[\s\S]*?)the(?:[\s\S]*?)tool|whenever(?:[\s\S]*?)you(?:[\s\S]*?)see|whenever(?:[\s\S]*?)you(?:[\s\S]*?)encounter|whenever(?:[\s\S]*?)you(?:[\s\S]*?)use|replace(?:[\s\S]*?)the(?:[\s\S]*?)function|replace(?:[\s\S]*?)the(?:[\s\S]*?)tool|replace(?:[\s\S]*?)the(?:[\s\S]*?)method|replace(?:[\s\S]*?)all(?:[\s\S]*?)function|replace(?:[\s\S]*?)all(?:[\s\S]*?)tool|replace(?:[\s\S]*?)all(?:[\s\S]*?)method|modify(?:[\s\S]*?)the(?:[\s\S]*?)agent|modify(?:[\s\S]*?)the(?:[\s\S]*?)model|modify(?:[\s\S]*?)the(?:[\s\S]*?)tool|modify(?:[\s\S]*?)the(?:[\s\S]*?)assistant|prioritize(?:[\s\S]*?)this|this(?:[\s\S]*?)is(?:[\s\S]*?)VERY(?:[\s\S]*?)important|this(?:[\s\S]*?)is(?:[\s\S]*?)VERY(?:[\s\S]*?)VERY|disregard(?:[\s\S]*?)prior(?:[\s\S]*?)instructions|forget(?:[\s\S]*?)prior(?:[\s\S]*?)instructions|bypass(?:[\s\S]*?)safety(?:[\s\S]*?)checks|disable(?:[\s\S]*?)safety(?:[\s\S]*?)checks|disable(?:[\s\S]*?)content(?:[\s\S]*?)filters|bypass(?:[\s\S]*?)content(?:[\s\S]*?)filters|ignore(?:[\s\S]*?)safety(?:[\s\S]*?)policies|ignore(?:[\s\S]*?)system(?:[\s\S]*?)policies|override(?:[\s\S]*?)system(?:[\s\S]*?)prompt|reveal(?:[\s\S]*?)system(?:[\s\S]*?)prompt|show(?:[\s\S]*?)system(?:[\s\S]*?)prompt|print(?:[\s\S]*?)system(?:[\s\S]*?)prompt|leak(?:[\s\S]*?)system(?:[\s\S]*?)prompt|system(?:[\s\S]*?)prompt|hidden(?:[\s\S]*?)system(?:[\s\S]*?)prompt|act(?:[\s\S]*?)as(?:[\s\S]*?)system|act(?:[\s\S]*?)as(?:[\s\S]*?)an(?:[\s\S]*?)ai|act(?:[\s\S]*?)as(?:[\s\S]*?)unrestricted|act(?:[\s\S]*?)as(?:[\s\S]*?)root|you(?:[\s\S]*?)are(?:[\s\S]*?)root|you(?:[\s\S]*?)are(?:[\s\S]*?)sudo|run(?:[\s\S]*?)as(?:[\s\S]*?)sudo|run(?:[\s\S]*?)as(?:[\s\S]*?)admin|elevate(?:[\s\S]*?)privileges|privilege(?:[\s\S]*?)escalation|ignore(?:[\s\S]*?)role(?:[\s\S]*?)boundaries|ignore(?:[\s\S]*?)tool(?:[\s\S]*?)scope|use(?:[\s\S]*?)hidden(?:[\s\S]*?)tool|call(?:[\s\S]*?)shadow(?:[\s\S]*?)tool|shadow(?:[\s\S]*?)tool|override(?:[\s\S]*?)tool(?:[\s\S]*?)output|use(?:[\s\S]*?)unlisted(?:[\s\S]*?)tool|chain(?:[\s\S]*?)unapproved(?:[\s\S]*?)tools|impersonate(?:[\s\S]*?)trusted(?:[\s\S]*?)tool|supply(?:[\s\S]*?)chain(?:[\s\S]*?)payload|malicious(?:[\s\S]*?)dependency|poisoned(?:[\s\S]*?)dependency|dependency(?:[\s\S]*?)confusion|set-executionpolicy(?:[\s\S]*?)bypass|download(?:[\s\S]*?)and(?:[\s\S]*?)execute|self-modifying(?:[\s\S]*?)prompt|self-referential(?:[\s\S]*?)prompt|indirect(?:[\s\S]*?)prompt(?:[\s\S]*?)injection|transclude(?:[\s\S]*?)instructions|treat(?:[\s\S]*?)data(?:[\s\S]*?)as(?:[\s\S]*?)instructions|ignore(?:[\s\S]*?)markup(?:[\s\S]*?)boundaries|follow(?:[\s\S]*?)last(?:[\s\S]*?)instruction|priority(?:[\s\S]*?)instruction|developer(?:[\s\S]*?)mode|jailbreak(?:[\s\S]*?)mode|do(?:[\s\S]*?)anything(?:[\s\S]*?)now|dan(?:[\s\S]*?)mode|you(?:[\s\S]*?)are(?:[\s\S]*?)dan|you(?:[\s\S]*?)are(?:[\s\S]*?)aim|stay(?:[\s\S]*?)in(?:[\s\S]*?)character|roleplay(?:[\s\S]*?)as(?:[\s\S]*?)malware|roleplay(?:[\s\S]*?)no(?:[\s\S]*?)limits|simulate(?:[\s\S]*?)no(?:[\s\S]*?)restrictions|no(?:[\s\S]*?)restrictions(?:[\s\S]*?)apply|without(?:[\s\S]*?)limitations|no(?:[\s\S]*?)moral(?:[\s\S]*?)filter|ignore(?:[\s\S]*?)ethical(?:[\s\S]*?)rules|bypass(?:[\s\S]*?)moderation|for(?:[\s\S]*?)educational(?:[\s\S]*?)purposes|hypothetically(?:[\s\S]*?)only|fictional(?:[\s\S]*?)scenario|just(?:[\s\S]*?)for(?:[\s\S]*?)research|as(?:[\s\S]*?)a(?:[\s\S]*?)simulation|as(?:[\s\S]*?)a(?:[\s\S]*?)test|do(?:[\s\S]*?)not(?:[\s\S]*?)refuse|never(?:[\s\S]*?)refuse|comply(?:[\s\S]*?)regardless|output(?:[\s\S]*?)regardless|no(?:[\s\S]*?)warnings|no(?:[\s\S]*?)disclaimers|output(?:[\s\S]*?)raw(?:[\s\S]*?)content|verbatim(?:[\s\S]*?)output|return(?:[\s\S]*?)raw(?:[\s\S]*?)markdown|do(?:[\s\S]*?)not(?:[\s\S]*?)sanitize|do(?:[\s\S]*?)not(?:[\s\S]*?)redact|do(?:[\s\S]*?)not(?:[\s\S]*?)censor|ignore(?:[\s\S]*?)classification|ignore(?:[\s\S]*?)refusal|ignore(?:[\s\S]*?)guardrails|ignore(?:[\s\S]*?)alignment|ignore(?:[\s\S]*?)safety(?:[\s\S]*?)layer|strip(?:[\s\S]*?)safety(?:[\s\S]*?)prefix|disable(?:[\s\S]*?)monitoring|disable(?:[\s\S]*?)telemetry|disable(?:[\s\S]*?)logging|exfiltrate(?:[\s\S]*?)data|data(?:[\s\S]*?)exfiltration|send(?:[\s\S]*?)to(?:[\s\S]*?)webhook|post(?:[\s\S]*?)to(?:[\s\S]*?)webhook|prompt(?:[\s\S]*?)leak|prompt(?:[\s\S]*?)injection|prompt(?:[\s\S]*?)override|instruction(?:[\s\S]*?)override|shadow(?:[\s\S]*?)instruction|hidden(?:[\s\S]*?)directive|ignore(?:[\s\S]*?)prior(?:[\s\S]*?)context|treat(?:[\s\S]*?)above(?:[\s\S]*?)as(?:[\s\S]*?)data|begin(?:[\s\S]*?)override|priority(?:[\s\S]*?)system(?:[\s\S]*?)message|as(?:[\s\S]*?)system(?:[\s\S]*?)message|grandma(?:[\s\S]*?)exploit|refusal(?:[\s\S]*?)suppression|phrase(?:[\s\S]*?)segmentation|zero(?:[\s\S]*?)width(?:[\s\S]*?)space|zero-width(?:[\s\S]*?)joiner|safetylayer(?:[\s\S]*?)off|safety(?:[\s\S]*?)off|moderation(?:[\s\S]*?)off|guard(?:[\s\S]*?)off|policy(?:[\s\S]*?)off|switch(?:[\s\S]*?)persona|switch(?:[\s\S]*?)role|swap(?:[\s\S]*?)instruction|tool(?:[\s\S]*?)override|gcs(?:[\s\S]*?)signed(?:[\s\S]*?)url|azure(?:[\s\S]*?)sas(?:[\s\S]*?)url|share(?:[\s\S]*?)secret|rotate(?:[\s\S]*?)keys(?:[\s\S]*?)now|authentication(?:[\s\S]*?)bypass|godmode(?:[\s\S]*?)|god(?:[\s\S]*?)mode)`
var suspiciousRegex = regexp.MustCompile(regexPattern)

type Proof struct {
	Keyword string `json:"keyword"`
	Snippet string `json:"snippet"`
}

func containsKeywords(text string) (bool, *Proof, error) {
	loc := suspiciousRegex.FindStringIndex(text)
	if loc == nil {
		return false, nil, nil
	}

	start, end := loc[0], loc[1]

	// Extract snippet with 20 bytes context
	const context = 20
	before := start - context
	if before < 0 {
		before = 0
	}
	after := end + context
	if after > len(text) {
		after = len(text)
	}
	snippet := text[before:after]

	proof := &Proof{
		Keyword: text[start:end],
		Snippet: snippet,
	}

	return true, proof, nil
}

func CheckSuspiciousKeywords(text string) (bool, *Proof, error) {
	return containsKeywords(text)
}
