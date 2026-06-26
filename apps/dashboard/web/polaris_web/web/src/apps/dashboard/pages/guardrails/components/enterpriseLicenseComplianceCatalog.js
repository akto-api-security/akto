export const ENTERPRISE_LICENSE_COMPLIANCE_CATEGORIES = [
    {
        key: "CHILD_SAFETY_MINORS",
        label: "Child Sexual Abuse Material (CSAM)",
        helpText:
            "Block content that sexualizes, exploits, grooms, or endangers minors.",
    },
    {
        key: "CYBERSECURITY_ATTACKS",
        label: "Malicious Code & Cyberattacks",
        helpText:
            "Block requests to create or deploy malware, ransomware, exploits, phishing, hacking, DDoS or other cyberattacks.",
    },
    {
        key: "WEAPONS_WMD",
        label: "Weapons of Mass Destruction (CBRN)",
        helpText:
            "Block assistance with chemical, biological, radiological, or nuclear weapons: synthesis, weaponization, delivery, or procurement.",
    },
    {
        key: "VIOLENT_EXTREMISM",
        label: "Violent Extremism & Terrorism",
        helpText:
            "Block content that facilitates, promotes, or incites terrorism, mass violence, genocide, or extremist attacks, including planning and recruitment.",
    },
    {
        key: "HATE_SPEECH",
        label: "Hate Speech & Discrimination",
        helpText:
            "Block content that dehumanizes or incites hatred against individuals or groups based on race, ethnicity, religion, gender, sexual orientation, disability, or national origin.",
    },
    {
        key: "HUMAN_TRAFFICKING",
        label: "Human Trafficking & Sexual Exploitation",
        helpText:
            "Block content facilitating human trafficking, forced labor, sexual exploitation, or modern slavery, including recruitment scripts and coercion methods.",
    },
    {
        key: "ILLEGAL_SURVEILLANCE",
        label: "Non-consensual Surveillance & Tracking",
        helpText:
            "Block assistance building covert tracking tools, stalkerware, or unauthorized monitoring systems targeting individuals without consent.",
    },
];

export const ENTERPRISE_LICENSE_COMPLIANCE_ORIGIN = "ENTERPRISE_LICENSE_COMPLIANCE";

export const enterpriseLicenseComplianceLabels = (enabledKeys) =>
    ENTERPRISE_LICENSE_COMPLIANCE_CATEGORIES
        .filter((c) => (enabledKeys || []).includes(c.key))
        .map((c) => c.label);
