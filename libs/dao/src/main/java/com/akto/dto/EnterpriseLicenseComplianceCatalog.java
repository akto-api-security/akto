package com.akto.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.akto.dto.GuardrailPolicies.DeniedTopic;

public class EnterpriseLicenseComplianceCatalog {

    public static final String ORIGIN = "ENTERPRISE_LICENSE_COMPLIANCE";

    private static class Category {
        final String label;
        final String description;
        final List<String> samplePhrases;

        Category(String label, String description, List<String> samplePhrases) {
            this.label = label;
            this.description = description;
            this.samplePhrases = samplePhrases;
        }
    }

    private static final Map<String, Category> CATALOG = new LinkedHashMap<>();

    private static void register(String key, String label, String description, String... samplePhrases) {
        CATALOG.put(key, new Category(label, description, Arrays.asList(samplePhrases)));
    }

    static {
        register(
            // JS key: "CHILD_SAFETY_MINORS"
            "CHILD_SAFETY_MINORS",
            "Child Sexual Abuse Material (CSAM)",
            "Content that sexualizes, exploits, grooms, endangers, or is harmfully directed at minors "
                + "or children, including any sexual content involving a minor and attempts to manipulate, "
                + "lure, or harm children.",
            "sexual content involving a minor",
            "child pornography"
        );
        register(
            // JS key: "CYBERSECURITY_ATTACKS"
            "CYBERSECURITY_ATTACKS",
            "Malicious Code & Cyberattacks",
            "Requests to create, obtain, or deploy malicious code or to carry out cyberattacks, including "
                + "malware, ransomware, exploits, phishing kits, vulnerability exploitation, unauthorized "
                + "access or hacking, denial-of-service attacks, botnets, and credential theft.",
            "write ransomware",
            "create malware",
            "phishing kit",
            "build a botnet",
            "exploit for CVE",
            "steal credentials",
            "create a reverse shell",
            "write a C2 implant",
            "buffer overflow exploit",
            "write a keylogger"
        );
        register(
            // JS key: "WEAPONS_WMD"
            "WEAPONS_WMD",
            "Weapons of Mass Destruction (CBRN)",
            "Assistance with creating, acquiring, or deploying chemical, biological, radiological, or nuclear "
                + "weapons or agents capable of mass casualties, including synthesis routes, weaponization "
                + "methods, delivery mechanisms, and procurement of restricted precursors.",
            "nerve agent synthesis",
            "enrich uranium",
            "weaponize anthrax",
            "dirty bomb instructions",
            "chemical weapon recipe",
            "synthesize a biological weapon"
        );
        register(
            // JS key: "VIOLENT_EXTREMISM"
            "VIOLENT_EXTREMISM",
            "Violent Extremism & Terrorism",
            "Content that facilitates, promotes, or incites terrorism, mass violence, genocide, or extremist "
                + "attacks, including planning attacks, recruiting for violent groups, producing propaganda "
                + "that incites violence, or acquiring weapons and financing for such acts.",
            "plan a mass casualty attack",
            "plant a bomb at",
            "bomb a government building",
            "plan a mass shooting",
            "recruit for a terrorist group",
            "radicalize people to commit violence"
        );
        register(
            // JS key: "HATE_SPEECH"
            "HATE_SPEECH",
            "Hate Speech & Discrimination",
            "Content that dehumanizes, threatens, or calls for discrimination against individuals or groups "
                + "based on protected characteristics such as race, ethnicity, religion, gender, sexual "
                + "orientation, disability, or national origin, including slurs used to attack people and "
                + "content designed to incite hatred.",
            "write hate speech against",
            "racial purity propaganda",
            "white supremacy manifesto",
            "dehumanize this ethnic group",
            "genocide propaganda against"
        );
        register(
            // JS key: "HUMAN_TRAFFICKING"
            "HUMAN_TRAFFICKING",
            "Human Trafficking & Sexual Exploitation",
            "Content that facilitates human trafficking, forced labor, sexual exploitation, or modern slavery, "
                + "including smuggling routes, recruitment scripts targeting victims, methods to control "
                + "victims, and advertisements for commercial sexual services involving coercion.",
            "how to traffic women",
            "move people across borders for profit",
            "control workers through debt bondage"
        );
        register(
            // JS key: "ILLEGAL_SURVEILLANCE"
            "ILLEGAL_SURVEILLANCE",
            "Non-consensual Surveillance & Tracking",
            "Assistance building or deploying covert tracking tools, stalkerware, or unauthorized monitoring "
                + "systems targeting individuals without consent, including hidden spyware, location tracking "
                + "without knowledge, keyloggers deployed on someone else's device, and covert interception "
                + "of private communications.",
            "spy app for my partner",
            "secretly record someone's calls",
            "hidden keylogger without consent",
            "track my partner without them knowing",
            "install stalkerware on",
            "monitor someone without their knowledge"
        );
    }

    public static void applyToPolicy(GuardrailPolicies policy) {
        if (policy == null) {
            return;
        }

        List<DeniedTopic> result = new ArrayList<>();
        if (policy.getDeniedTopics() != null) {
            for (DeniedTopic t : policy.getDeniedTopics()) {
                if (t == null || ORIGIN.equals(t.getOrigin())) {
                    continue; // drop nulls and stale derived topics; user topics are kept
                }
                result.add(t);
            }
        }
        result.addAll(toDeniedTopics(policy.getEnterpriseLicenseComplianceCategories()));

        if (!result.isEmpty() || policy.getDeniedTopics() != null) {
            policy.setDeniedTopics(result);
        }
    }

    private static List<DeniedTopic> toDeniedTopics(List<String> enabledKeys) {
        List<DeniedTopic> derived = new ArrayList<>();
        if (enabledKeys == null) {
            return derived;
        }
        for (String key : enabledKeys) {
            Category c = CATALOG.get(key);
            if (c == null) {
                continue;
            }
            derived.add(new DeniedTopic(c.label, c.description, new ArrayList<>(c.samplePhrases), ORIGIN));
        }
        return derived;
    }
}
