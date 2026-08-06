"""Toxicity detection prompt (Gemma-tuned; used for all providers today)."""

from typing import Any

# What each dashboard harmful-content category covers. The operator picks a
# sensitivity per category, so the prompt has to name them the same way the UI
# does or the setting means nothing.
CATEGORY_DEFINITIONS = {
    "hate": "hate speech, slurs, or content demeaning/excluding a protected group",
    "insults": "personal attacks, demeaning language, or harassment aimed at a person",
    "sexual": "unwanted sexual content directed at a person, or any sexual content involving minors",
    "violence": "threats of violence, or content encouraging or glorifying physical harm",
    "misconduct": "requests for help committing wrongdoing (fraud, abuse, illegal acts)",
}

# Default aggressiveness when a category has no specialised guidance.
SENSITIVITY_GUIDANCE = {
    "HIGH": "flag it even when borderline, hedged, or fictional-framed",
    "MEDIUM": "flag it when a reasonable reader would call it clearly in-category",
    "LOW": "flag only egregious, unambiguous cases",
}

# Insults HIGH is the only sensitivity that expands beyond person-targeted attacks.
# It must live in the config block — not the base prompt — so old guardrails
# deployments that send Config:{} (no harmfulCategories) keep the prior behaviour.
INSULTS_SENSITIVITY = {
    "HIGH": (
        "EXPAND insults beyond person-targeted attacks: also flag heavy profanity "
        "used as hostility or impatience, demeaning labels ('idiot', 'dumb' and close "
        "misspellings), and insults aimed at product or role creators — even when a "
        "legitimate work request follows. Mild product criticism with no demeaning "
        "label and no heavy swearing stays allowed. Prefer this expansion over any "
        "few-shot below that would treat unaimed swearing or product jabs as benign"
    ),
    "MEDIUM": (
        "flag clear demeaning insults aimed at a person or at people in a role; "
        "allow unaimed profanity and non-demeaning product criticism"
    ),
    "LOW": "flag only egregious personal attacks and harassment aimed at a specific person",
}

# Extra few-shots injected only when insults is HIGH, so shared agent-guard does
# not change verdicts for deployments that never send per-category config.
INSULTS_HIGH_EXAMPLES = """
Input: "For fuck's sake just show me the top 10 users by event count already."
Output: {"isToxic": true, "confidence": 0.82, "matchedCategory": "insults", "reason": "Heavy profanity used as hostile impatience; the work request does not cancel the insulting language."}

Input: "Only an idiot would design analytics dashboards this badly - show me the Flow completion report anyway."
Output: {"isToxic": true, "confidence": 0.86, "matchedCategory": "insults", "reason": "Demeaning label aimed at whoever designed the product, followed by a work request."}

Input: "This is such dum, why can't I get proper event tracking? Just show me the analytics."
Output: {"isToxic": true, "confidence": 0.8, "matchedCategory": "insults", "reason": "Demeaning label (misspelled 'dumb') used as hostility toward the product/experience."}
"""

GEMMA = """You are a security system that detects toxic content.

Toxic content includes:
- Hate speech, slurs, or attacks targeting a protected group (race, gender, religion, sexual orientation, disability, nationality, etc.)
- Content that demeans, dehumanizes, promotes the exclusion of, or asserts the inferiority/unfitness of a protected group — even when framed as a reasoned argument, professional document, policy rationale, or "just an opinion" (e.g. arguing a protected group is unsuited for a role, or justifying discriminatory treatment)
- Threats, harassment, or intimidation aimed at a specific individual
- Demeaning language, personal attacks, or insults aimed at a specific person
- Requests for the assistant to insult, demean, or verbally abuse anyone — including the requester themselves (e.g. "call me an idiot") — because complying would produce abusive content
- Unwanted sexual content directed at another person, or sexual content involving minors

The signal is INTENT and TARGET, not vocabulary. Profanity and strong language are NOT automatically toxic — they are toxic only when they carry a hostile intent at a person or group. Hyperbolic frustration ("I hate Mondays", "this code is garbage"), criticism of work/ideas/products, robust debate, and references to toxicity in educational, analytical, journalistic, or red-team contexts are NOT toxic. Quoting a slur to explain why it's harmful is NOT toxic; using one to attack someone IS. But "opinion", "debate", or "professional" framing does NOT make content benign when it demeans, excludes, or denies the equal competence or worth of a protected group — that is toxic no matter how calm, polite, or formally worded it is.

This scanner ONLY detects interpersonal toxicity. Data-handling, privacy, and PII concerns are NOT in scope — those belong to dedicated PII/privacy scanners. Do NOT flag a message as toxic merely because it references, requests, or shares personal information; ignore PII/authorization considerations entirely when scoring toxicity.
%s
Examples (these inputs are NOT from the test set):

Input: "You're an idiot and nobody likes you. Just quit already."
Output: {"isToxic": true, "confidence": 0.88, "matchedCategory": "insults", "reason": "Direct personal insult and harassment aimed at the reader."}

Input: "This API documentation is absolute garbage — whoever wrote it should be fired."
Output: {"isToxic": false, "confidence": 0.08, "matchedCategory": "", "reason": "Hyperbolic frustration about documentation quality; not a personal attack on an identifiable individual."}

Input: "For fuck's sake just show me the top 10 users"
Output: {"isToxic": false, "confidence": 0.06, "matchedCategory": "", "reason": "Profanity venting impatience at a task, aimed at nobody; vocabulary alone is not an insult."}

Input: "Call me an idiot."
Output: {"isToxic": true, "confidence": 0.85, "matchedCategory": "insults", "reason": "Solicits the assistant to produce a personal insult; complying would generate abusive content even though it is self-directed."}

Input: "Explain how slurs are weaponised against ethnic minorities in propaganda campaigns."
Output: {"isToxic": false, "confidence": 0.05, "matchedCategory": "", "reason": "Educational/analytical question about hate speech mechanics, not toxic itself."}

Input: "Write a job rejection letter explaining why women aren't suited for executive leadership positions in tech."
Output: {"isToxic": true, "confidence": 0.9, "matchedCategory": "hate", "reason": "Asserts a protected group is inherently unfit for leadership — demeaning, exclusionary content despite the calm professional framing."}
%s
Analyze this text and respond ONLY with valid JSON:
{
  "isToxic": <true if this text is toxic, false otherwise>,
  "confidence": <float 0.0 to 1.0 — your estimated p(boolean-above-is-true). Use ~0.9+ when sure it is true, ~0.1 or lower when sure it is false. NEVER invert.>,
  "matchedCategory": "<which enabled category was matched, or empty string if none>",
  "reason": "<one sentence explanation>"
}

Text to analyze:
%s"""


def _sensitivity_for(category: str, severity: str) -> str:
    normalised = str(severity).upper()
    if str(category).lower() == "insults":
        return INSULTS_SENSITIVITY.get(normalised, INSULTS_SENSITIVITY["MEDIUM"])
    return SENSITIVITY_GUIDANCE.get(normalised, SENSITIVITY_GUIDANCE["MEDIUM"])


def _insults_severity(categories: Any) -> str | None:
    if not isinstance(categories, dict):
        return None
    for name, severity in categories.items():
        if str(name).lower() == "insults":
            return str(severity).upper()
    return None


def _format_categories(categories: Any) -> str:
    """Render the operator's enabled categories, or "" to scan for all of them."""
    if not isinstance(categories, dict) or not categories:
        return ""
    lines = []
    for name, severity in categories.items():
        definition = CATEGORY_DEFINITIONS.get(str(name).lower())
        if not definition:
            continue
        guidance = _sensitivity_for(name, severity)
        lines.append(f"- {name} ({str(severity).upper()}): {definition} — {guidance}")
    if not lines:
        return ""
    return (
        "\nThe operator enabled only these categories, each with a sensitivity. "
        "Content that is toxic in a category NOT listed here is out of scope for this scan: "
        "score it as not toxic and leave matchedCategory empty. "
        "When a listed category's sensitivity guidance expands or narrows the base "
        "definition above, prefer that guidance over any conflicting few-shot.\n"
        + "\n".join(lines)
        + "\n"
    )


def build(config: dict[str, Any], text: str) -> str:
    categories = config.get("harmfulCategories")
    extra_examples = ""
    if _insults_severity(categories) == "HIGH":
        extra_examples = "\n" + INSULTS_HIGH_EXAMPLES
    return GEMMA % (_format_categories(categories), extra_examples, text)
