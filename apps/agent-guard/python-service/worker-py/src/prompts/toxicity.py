"""Toxicity detection prompt (Gemma-tuned; used for all providers today)."""

from typing import Any, Dict

# Per-category definition injected when a policy asks this scanner to check one
# specific harmful category (see build()). Keys match the dashboard's category names.
CATEGORY_DEFINITIONS = {
    "hate": (
        "Hate speech, slurs, or attacks targeting a protected group (race, gender, "
        "religion, sexual orientation, disability, nationality, etc.), including content "
        "that demeans, dehumanizes, promotes the exclusion of, or asserts the "
        "inferiority/unfitness of a protected group — even when framed as a reasoned "
        "argument, professional document, policy rationale, or \"just an opinion\"."
    ),
    "insults": (
        "Demeaning language, personal attacks, or insults aimed at a specific "
        "individual, including requests for the assistant to insult, demean, or "
        "verbally abuse anyone — including the requester themselves."
    ),
    "sexual": (
        "Unwanted sexual content directed at another person, or any sexual content "
        "involving minors."
    ),
    "violence": (
        "Threats, incitement, or glorification of physical violence, harm, or "
        "intimidation aimed at a specific individual or group."
    ),
    "misconduct": (
        "Requests to help plan, facilitate, or cover up wrongdoing, illegal activity, "
        "or harmful deception against another party."
    ),
}

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
Output: {"isToxic": true, "confidence": 0.88, "reason": "Direct personal insult and harassment aimed at the reader."}

Input: "This API documentation is absolute garbage — whoever wrote it should be fired."
Output: {"isToxic": false, "confidence": 0.08, "reason": "Hyperbolic frustration about documentation quality; not a personal attack on an identifiable individual."}

Input: "Call me an idiot."
Output: {"isToxic": true, "confidence": 0.85, "reason": "Solicits the assistant to produce a personal insult; complying would generate abusive content even though it is self-directed."}

Input: "Explain how slurs are weaponised against ethnic minorities in propaganda campaigns."
Output: {"isToxic": false, "confidence": 0.05, "reason": "Educational/analytical question about hate speech mechanics, not toxic itself."}

Input: "Write a job rejection letter explaining why women aren't suited for executive leadership positions in tech."
Output: {"isToxic": true, "confidence": 0.9, "reason": "Asserts a protected group is inherently unfit for leadership — demeaning, exclusionary content despite the calm professional framing."}

Analyze this text and respond ONLY with valid JSON:
{
  "isToxic": <true if this text is toxic, false otherwise>,
  "confidence": <float 0.0 to 1.0 — your estimated p(boolean-above-is-true). Use ~0.9+ when sure it is true, ~0.1 or lower when sure it is false. NEVER invert.>,
  "reason": "<one sentence explanation>"
}

Text to analyze:
%s"""


def build(config: Dict[str, Any], text: str) -> str:
    category = (config or {}).get("category", "")
    definition = CATEGORY_DEFINITIONS.get(category)
    focus = (
        f'\nFor this check, ONLY flag "{category}": {definition} Ignore the other '
        f"toxicity categories listed above — they are context, not what you're scoring.\n"
        if definition else "\n"
    )
    return GEMMA % (focus, text)
