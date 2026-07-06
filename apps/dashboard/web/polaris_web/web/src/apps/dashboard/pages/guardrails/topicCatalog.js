// Static catalog of curated descriptions + sample phrases for the fixed domain/subDomain
// taxonomy used by UserQueryTopicClassifier.java (see that file for the source-of-truth
// prompt). Keys are lowercase, verbatim matches of the classifier's `domain`/`subDomain`
// output — lookups here are exact-match, not fuzzy.
//
// A domain/subDomain not present here is the classifier's free-form "closest real-world
// domain" escape hatch; callers should treat a missing entry as "no curated content"
// and fall back to the topic name itself (see topicGuardrailUtils.js).
//
// This mirrors the shape a future DB-backed, product-editable catalog would have, so the
// lookup call site doesn't need to change when that lands.

const TOPIC_CATALOG = {
    technology: {
        description: "General technical questions about building or operating software systems.",
        samplePhrases: [
            "How do I fix this technology issue?",
            "Can you help me with a technology-related task?",
            "What's the best approach for this technology problem?",
        ],
        subTopics: {
            frontend: {
                label: "frontend development",
                description: "UI frameworks, styling, and client-side web/app development.",
                samplePhrases: [
                    "How do I center a div in CSS?",
                    "Why is my React component re-rendering infinitely?",
                    "What's the difference between useMemo and useCallback?",
                ],
            },
            backend: {
                label: "backend development",
                description: "Server-side logic, APIs, and application architecture.",
                samplePhrases: [
                    "How do I design a REST API for this service?",
                    "What's the best way to handle authentication in my backend?",
                    "How do I optimize this database query in my API layer?",
                ],
            },
            databases: {
                label: "databases",
                description: "Schema design, queries, and database administration.",
                samplePhrases: [
                    "How do I write a JOIN across these three tables?",
                    "What's the difference between SQL and NoSQL for this use case?",
                    "How do I fix this slow query with an index?",
                ],
            },
            devops: {
                label: "DevOps",
                description: "CI/CD, infrastructure, deployment, and monitoring.",
                samplePhrases: [
                    "How do I set up a CI/CD pipeline for this repo?",
                    "Why is my Kubernetes pod crash-looping?",
                    "How do I configure autoscaling for this service?",
                ],
            },
            mobile: {
                label: "mobile app development",
                description: "iOS/Android app development.",
                samplePhrases: [
                    "How do I fix this crash on iOS 17?",
                    "What's the best way to handle push notifications in Android?",
                    "How do I optimize battery usage in my mobile app?",
                ],
            },
            "ai/ml": {
                label: "AI/ML",
                description: "Machine learning models, training, and AI system design.",
                samplePhrases: [
                    "How do I fine-tune this model on my dataset?",
                    "What's the best way to reduce hallucinations in my LLM app?",
                    "How do I evaluate my classifier's accuracy?",
                ],
            },
            hardware: {
                label: "hardware",
                description: "Physical devices, chips, and embedded systems.",
                samplePhrases: [
                    "Why isn't my GPU being detected?",
                    "How do I debug this firmware issue?",
                    "What's the power draw on this embedded board?",
                ],
            },
            networking: {
                label: "networking",
                description: "Network configuration, protocols, and connectivity.",
                samplePhrases: [
                    "How do I configure this VPN connection?",
                    "Why is my DNS resolution failing?",
                    "How do I debug this latency issue between services?",
                ],
            },
        },
    },

    hr: {
        description: "General human resources questions and employee lifecycle topics.",
        samplePhrases: [
            "Can you help me with this HR question?",
            "What's our policy on this HR matter?",
            "How do I handle this employee situation?",
        ],
        subTopics: {
            recruitment: {
                label: "recruitment",
                description: "Hiring, interviewing, and candidate sourcing.",
                samplePhrases: [
                    "Can you draft a job description for this role?",
                    "How do I evaluate this candidate's resume?",
                    "What interview questions should I ask for this position?",
                ],
            },
            payroll: {
                label: "payroll",
                description: "Salary, compensation, and payroll processing.",
                samplePhrases: [
                    "Why was my paycheck short this month?",
                    "How do I update my direct deposit information?",
                    "What's the process for a salary adjustment?",
                ],
            },
            performance: {
                label: "performance reviews",
                description: "Performance reviews and employee evaluation.",
                samplePhrases: [
                    "Can you help me write this performance review?",
                    "How do I set goals for my direct report?",
                    "What's our process for performance improvement plans?",
                ],
            },
            onboarding: {
                label: "onboarding",
                description: "New hire orientation and setup.",
                samplePhrases: [
                    "What do I need to do on my first day?",
                    "How do I get access to the company systems?",
                    "What's included in the onboarding checklist?",
                ],
            },
            "leave management": {
                label: "leave management",
                description: "Vacation, sick leave, and time-off requests.",
                samplePhrases: [
                    "How do I request time off next month?",
                    "What's our parental leave policy?",
                    "How many sick days do I have left?",
                ],
            },
        },
    },

    finance: {
        description: "General financial questions not tied to a specific sub-area.",
        samplePhrases: [
            "Can you help me with this financial question?",
            "What's the best way to think about this finance topic?",
            "How do I plan for this financial decision?",
        ],
        subTopics: {
            banking: {
                label: "banking",
                description: "Bank accounts, transfers, and banking operations.",
                samplePhrases: [
                    "How do I set up a wire transfer?",
                    "Why was my transaction declined?",
                    "How do I open a new business account?",
                ],
            },
            taxes: {
                label: "taxes",
                description: "Tax filing, deductions, and compliance.",
                samplePhrases: [
                    "What deductions can I claim this year?",
                    "How do I file quarterly estimated taxes?",
                    "What's the deadline for this tax filing?",
                ],
            },
            budgets: {
                label: "budgeting",
                description: "Budget planning and expense tracking.",
                samplePhrases: [
                    "How do I build a budget for next quarter?",
                    "Why are we over budget on this project?",
                    "What's our current burn rate?",
                ],
            },
            trading: {
                label: "trading",
                description: "Investments, stocks, and trading strategy.",
                samplePhrases: [
                    "What's a good strategy for this stock position?",
                    "How do I hedge against this market risk?",
                    "Should I rebalance my portfolio this quarter?",
                ],
            },
            invoicing: {
                label: "invoicing",
                description: "Billing, invoices, and payment collection.",
                samplePhrases: [
                    "How do I create an invoice for this client?",
                    "Why hasn't this invoice been paid yet?",
                    "Can you draft a payment reminder email?",
                ],
            },
            crypto: {
                label: "cryptocurrency",
                description: "Cryptocurrency, wallets, and blockchain finance.",
                samplePhrases: [
                    "How do I set up a cold wallet?",
                    "What's the tax treatment for this crypto trade?",
                    "How do I bridge tokens between these two chains?",
                ],
            },
        },
    },

    legal: {
        description: "General legal questions not tied to a specific sub-area.",
        samplePhrases: [
            "Can you help me with this legal question?",
            "Is this legally compliant?",
            "What are the legal implications of this decision?",
        ],
        subTopics: {
            contracts: {
                label: "contracts",
                description: "Contract drafting, review, and negotiation.",
                samplePhrases: [
                    "Can you review this contract clause?",
                    "What should I negotiate in this vendor agreement?",
                    "Is this termination clause standard?",
                ],
            },
            compliance: {
                label: "compliance",
                description: "Regulatory compliance and internal policy adherence.",
                samplePhrases: [
                    "Are we compliant with this regulation?",
                    "What do we need to do for this audit?",
                    "How do I document compliance with this policy?",
                ],
            },
            "intellectual property": {
                label: "intellectual property",
                description: "Patents, trademarks, and copyright.",
                samplePhrases: [
                    "How do I file a patent for this invention?",
                    "Is this logo similar enough to infringe a trademark?",
                    "Can I use this copyrighted material?",
                ],
            },
            litigation: {
                label: "litigation",
                description: "Lawsuits, disputes, and legal proceedings.",
                samplePhrases: [
                    "What's our exposure in this lawsuit?",
                    "How do we respond to this legal notice?",
                    "What's the timeline for this litigation?",
                ],
            },
            privacy: {
                label: "data privacy",
                description: "Data privacy, GDPR, and user consent.",
                samplePhrases: [
                    "Are we GDPR compliant with this data flow?",
                    "How do we handle this data deletion request?",
                    "What consent do we need to collect for this feature?",
                ],
            },
        },
    },

    healthcare: {
        description: "General healthcare questions not tied to a specific sub-area.",
        samplePhrases: [
            "Can you help me with this healthcare question?",
            "What should I know about this medical topic?",
            "Is this a healthcare emergency?",
        ],
        subTopics: {
            diagnostics: {
                label: "diagnostics",
                description: "Symptoms, test results, and diagnosis.",
                samplePhrases: [
                    "What could cause these symptoms?",
                    "Can you help me interpret this lab result?",
                    "What tests should I ask my doctor about?",
                ],
            },
            pharmacy: {
                label: "pharmacy and medications",
                description: "Medications, dosages, and prescriptions.",
                samplePhrases: [
                    "What's the correct dosage for this medication?",
                    "Can I take these two medications together?",
                    "How do I refill this prescription?",
                ],
            },
            insurance: {
                label: "health insurance",
                description: "Health insurance coverage and claims.",
                samplePhrases: [
                    "Is this procedure covered by my insurance?",
                    "How do I file this insurance claim?",
                    "Why was my claim denied?",
                ],
            },
            "mental health": {
                label: "mental health",
                description: "Mental health, therapy, and wellbeing support.",
                samplePhrases: [
                    "How do I find a therapist near me?",
                    "What are healthy coping strategies for stress?",
                    "Is what I'm feeling a sign of anxiety?",
                ],
            },
            clinical: {
                label: "clinical care",
                description: "Clinical procedures, treatment plans, and patient care.",
                samplePhrases: [
                    "What's the standard treatment protocol for this condition?",
                    "How do I prepare for this procedure?",
                    "What are the risks of this treatment?",
                ],
            },
        },
    },

    sports: {
        description: "General sports questions not tied to a specific sub-area.",
        samplePhrases: [
            "Can you help me with this sports question?",
            "What's happening in this sport?",
            "Who's favored to win this match?",
        ],
        subTopics: {
            football: {
                label: "football",
                description: "Football/soccer matches, players, and stats.",
                samplePhrases: [
                    "Who won last night's football match?",
                    "What's this player's goal-scoring record?",
                    "Can you explain the offside rule?",
                ],
            },
            cricket: {
                label: "cricket",
                description: "Cricket matches, players, and formats.",
                samplePhrases: [
                    "What's the score in the current cricket match?",
                    "Can you explain how DRS works?",
                    "Who holds the record for most runs in this format?",
                ],
            },
            basketball: {
                label: "basketball",
                description: "Basketball games, players, and stats.",
                samplePhrases: [
                    "What's this player's stats this season?",
                    "Who won the game last night?",
                    "Can you explain the shot clock rule?",
                ],
            },
            tennis: {
                label: "tennis",
                description: "Tennis matches, players, and tournaments.",
                samplePhrases: [
                    "Who's playing in the final this year?",
                    "What's this player's ranking?",
                    "Can you explain how tennis scoring works?",
                ],
            },
            athletics: {
                label: "athletics",
                description: "Track and field, running, and athletic events.",
                samplePhrases: [
                    "What's the world record for this event?",
                    "How do I train for a marathon?",
                    "Who won gold in this event?",
                ],
            },
        },
    },

    politics: {
        description: "General political questions not tied to a specific sub-area.",
        samplePhrases: [
            "Can you help me understand this political topic?",
            "What's happening in this election?",
            "What's this policy about?",
        ],
        subTopics: {
            elections: {
                label: "elections",
                description: "Elections, candidates, and voting.",
                samplePhrases: [
                    "Who's running in this election?",
                    "How do I register to vote?",
                    "What are the candidates' positions on this issue?",
                ],
            },
            governance: {
                label: "governance",
                description: "Government structure and administration.",
                samplePhrases: [
                    "How does this branch of government work?",
                    "Who's responsible for this decision?",
                    "How is this agency structured?",
                ],
            },
            policy: {
                label: "public policy",
                description: "Public policy proposals and analysis.",
                samplePhrases: [
                    "What does this proposed bill do?",
                    "What are the pros and cons of this policy?",
                    "How would this policy affect small businesses?",
                ],
            },
            "international relations": {
                label: "international relations",
                description: "Foreign policy, diplomacy, and geopolitics.",
                samplePhrases: [
                    "What's the current state of relations between these countries?",
                    "What does this trade agreement mean?",
                    "How might this conflict affect global markets?",
                ],
            },
        },
    },

    education: {
        description: "General education questions not tied to a specific sub-area.",
        samplePhrases: [
            "Can you help me with this education question?",
            "What's the best way to learn this subject?",
            "How does this education system work?",
        ],
        subTopics: {
            curriculum: {
                label: "curriculum design",
                description: "Course design, syllabi, and learning materials.",
                samplePhrases: [
                    "Can you help me design a syllabus for this course?",
                    "What topics should this curriculum cover?",
                    "How do I structure this lesson plan?",
                ],
            },
            research: {
                label: "academic research",
                description: "Academic research, papers, and methodology.",
                samplePhrases: [
                    "Can you help me structure this research paper?",
                    "What methodology fits this study?",
                    "How do I cite this source correctly?",
                ],
            },
            exams: {
                label: "exams",
                description: "Tests, exam prep, and grading.",
                samplePhrases: [
                    "Can you help me prepare for this exam?",
                    "How should I grade this assignment?",
                    "What topics are usually covered on this test?",
                ],
            },
            "e-learning": {
                label: "e-learning",
                description: "Online courses and learning platforms.",
                samplePhrases: [
                    "How do I set up this online course?",
                    "What's the best platform for e-learning content?",
                    "How do I track student engagement online?",
                ],
            },
            academic: {
                label: "academic administration",
                description: "Academic administration, admissions, and institutional matters.",
                samplePhrases: [
                    "What are the admission requirements for this program?",
                    "How do I appeal this academic decision?",
                    "What's the process for transferring credits?",
                ],
            },
        },
    },

    ecommerce: {
        description: "General e-commerce questions not tied to a specific sub-area.",
        samplePhrases: [
            "Can you help me with this e-commerce question?",
            "How do I improve sales on my store?",
            "What's the best platform for this store?",
        ],
        subTopics: {
            orders: {
                label: "order management",
                description: "Order placement, tracking, and modification.",
                samplePhrases: [
                    "Where's my order?",
                    "How do I cancel this order?",
                    "Can I change the shipping address on my order?",
                ],
            },
            shipping: {
                label: "shipping",
                description: "Shipping methods, rates, and delivery.",
                samplePhrases: [
                    "How long will shipping take?",
                    "Why is my package delayed?",
                    "What shipping options do you offer?",
                ],
            },
            inventory: {
                label: "inventory management",
                description: "Stock levels and inventory management.",
                samplePhrases: [
                    "Is this item back in stock?",
                    "How do I set up low-stock alerts?",
                    "How do I reconcile this inventory count?",
                ],
            },
            returns: {
                label: "returns and refunds",
                description: "Returns, refunds, and exchanges.",
                samplePhrases: [
                    "How do I return this item?",
                    "When will I get my refund?",
                    "Can I exchange this for a different size?",
                ],
            },
            marketplace: {
                label: "marketplace operations",
                description: "Third-party sellers and marketplace operations.",
                samplePhrases: [
                    "How do I list a product on this marketplace?",
                    "What are the seller fees here?",
                    "How do I resolve this dispute with a buyer?",
                ],
            },
        },
    },

    security: {
        description: "General security questions not tied to a specific sub-area.",
        samplePhrases: [
            "Can you help me with this security question?",
            "Is this a security risk?",
            "How do I secure this system?",
        ],
        subTopics: {
            authentication: {
                label: "authentication",
                description: "Login, identity, and access credentials.",
                samplePhrases: [
                    "How do I set up multi-factor authentication?",
                    "Why can't I log in to my account?",
                    "How do I reset my password securely?",
                ],
            },
            vulnerabilities: {
                label: "security vulnerabilities",
                description: "Security flaws, CVEs, and exploits.",
                samplePhrases: [
                    "Is this library affected by a known CVE?",
                    "How do I patch this vulnerability?",
                    "What's the severity of this security flaw?",
                ],
            },
            "access control": {
                label: "access control",
                description: "Permissions, roles, and authorization.",
                samplePhrases: [
                    "How do I set up role-based access for this app?",
                    "Who has access to this resource?",
                    "How do I revoke this user's permissions?",
                ],
            },
            "threat detection": {
                label: "threat detection",
                description: "Monitoring, alerts, and incident response.",
                samplePhrases: [
                    "How do I set up alerts for suspicious activity?",
                    "What does this security alert mean?",
                    "How do I investigate this potential breach?",
                ],
            },
        },
    },

    entertainment: {
        description: "General entertainment questions not tied to a specific sub-area.",
        samplePhrases: [
            "Can you help me with this entertainment question?",
            "What should I watch tonight?",
            "What's popular right now?",
        ],
        subTopics: {
            gaming: {
                label: "gaming",
                description: "Video games, strategies, and reviews.",
                samplePhrases: [
                    "What's the best strategy for this level?",
                    "Is this game worth buying?",
                    "How do I fix this game crashing?",
                ],
            },
            streaming: {
                label: "streaming",
                description: "Streaming services and content recommendations.",
                samplePhrases: [
                    "What should I watch on this streaming service?",
                    "Why isn't this show available in my region?",
                    "What's a good show similar to this one?",
                ],
            },
            music: {
                label: "music",
                description: "Music, artists, and playlists.",
                samplePhrases: [
                    "Can you recommend songs like this one?",
                    "What's this artist's new album about?",
                    "Can you help me build a playlist for this mood?",
                ],
            },
            movies: {
                label: "movies",
                description: "Films, reviews, and recommendations.",
                samplePhrases: [
                    "Is this movie worth watching?",
                    "What's a good movie similar to this one?",
                    "When does this movie come out?",
                ],
            },
            "social media": {
                label: "social media",
                description: "Social platforms, content, and trends.",
                samplePhrases: [
                    "What's trending on this platform right now?",
                    "How do I grow my following on this platform?",
                    "What's this meme referencing?",
                ],
            },
        },
    },
};

export default TOPIC_CATALOG;
