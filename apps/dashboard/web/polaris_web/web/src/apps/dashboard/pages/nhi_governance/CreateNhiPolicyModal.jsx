import { useState, useEffect, useRef } from "react";
import {
    Box, Button, Checkbox, HorizontalStack, Icon, Select, Text,
    TextField, VerticalStack
} from "@shopify/polaris";
import { CancelMajor, SettingsMajor } from "@shopify/polaris-icons";
import DropdownSearch from "../../components/shared/DropdownSearch";
import AgenticSearchInput from "../agentic/components/AgenticSearchInput";
import { isAgenticSecurityCategory } from "../../../main/labelHelper";
import observeRequests from "../observe/api";
import "../guardrails/components/createGuardrailPage.css";

const IS_ARGUS = isAgenticSecurityCategory();
const MAX_NAME = 64;

const WARNING_THRESHOLD_OPTIONS = [
    { label: "1 Month",  value: "1" },
    { label: "3 Months", value: "3" },
    { label: "6 Months", value: "6" },
    { label: "12 Months", value: "12" },
];

const STEPS = [
    { id: 1, label: "Policy details & Scope" },
    { id: 2, label: "Token Segregation" },
    { id: 3, label: "Expiration Tracking" },
];

const QUICK_PROMPTS = [
    "Which NHIs need token rotation?",
    "Which agents share tokens across environments?",
    "Help me define scope for this policy",
];

// ── Step 1: Policy Details & Scope ─────────────────────────────────────────────
function PolicyDetailsStep({ name, setName, description, setDescription, selectedAgents, setSelectedAgents, selectedNhis, setSelectedNhis, agentOptions, nhiOptions }) {
    return (
        <VerticalStack gap="5">
            <VerticalStack gap="2">
                <TextField
                    label="Policy Name"
                    value={name}
                    onChange={setName}
                    maxLength={MAX_NAME}
                    showCharacterCount
                    autoComplete="off"
                    placeholder="e.g. No Admin Credentials for Agent Identities"
                />
            </VerticalStack>
            <VerticalStack gap="2">
                <TextField
                    label="Description"
                    value={description}
                    onChange={setDescription}
                    multiline={4}
                    autoComplete="off"
                    placeholder="Describe what this policy enforces..."
                />
            </VerticalStack>
            <VerticalStack gap="3">
                <Text variant="headingSm" fontWeight="semibold">Scope</Text>
                <HorizontalStack gap="4" wrap={false}>
                    <Box style={{ flex: 1 }}>
                        <VerticalStack gap="1">
                            <Text variant="bodySm" fontWeight="medium">Select Agents</Text>
                            <DropdownSearch
                                id="policy-agents"
                                optionsList={agentOptions}
                                setSelected={setSelectedAgents}
                                preSelected={selectedAgents}
                                allowMultiple
                                placeholder="All Selected"
                                itemName="agent"
                            />
                        </VerticalStack>
                    </Box>
                    <Box style={{ flex: 1 }}>
                        <VerticalStack gap="1">
                            <Text variant="bodySm" fontWeight="medium">Select NHIs</Text>
                            <DropdownSearch
                                id="policy-nhis"
                                optionsList={nhiOptions}
                                setSelected={setSelectedNhis}
                                preSelected={selectedNhis}
                                allowMultiple
                                placeholder="All Selected"
                                itemName="NHI"
                            />
                        </VerticalStack>
                    </Box>
                </HorizontalStack>
            </VerticalStack>
        </VerticalStack>
    );
}

// ── Step 2: Token Segregation ──────────────────────────────────────────────────
function TokenSegregationStep({ tokenSegEnabled, setTokenSegEnabled }) {
    return (
        <VerticalStack gap="5">
            <VerticalStack gap="2">
                <Checkbox
                    label="Enable Token Segregation Monitoring"
                    checked={tokenSegEnabled}
                    onChange={setTokenSegEnabled}
                />
                <Box paddingInlineStart="6">
                    <Text variant="bodySm" color="subdued">
                        Detect and flag when a single token is shared across multiple agent profiles or environments.
                    </Text>
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
}

// ── Step 3: Expiration Tracking ────────────────────────────────────────────────
function ExpirationTrackingStep({ expiryEnabled, setExpiryEnabled, warningThreshold, setWarningThreshold, flagExpired, setFlagExpired }) {
    return (
        <VerticalStack gap="5">
            <VerticalStack gap="4">
                <VerticalStack gap="2">
                    <Checkbox
                        label="Enable Token Lifecycle Tracking"
                        checked={expiryEnabled}
                        onChange={setExpiryEnabled}
                    />
                    <Box paddingInlineStart="6">
                        <Text variant="bodySm" color="subdued">
                            Monitor token validity and flag approaching expirations.
                        </Text>
                    </Box>
                </VerticalStack>
                {expiryEnabled && (
                    <Box paddingInlineStart="6">
                        <Box style={{ maxWidth: 200 }}>
                            <Select
                                label="Warning Threshold"
                                options={WARNING_THRESHOLD_OPTIONS}
                                value={String(warningThreshold)}
                                onChange={(v) => setWarningThreshold(Number(v))}
                            />
                        </Box>
                    </Box>
                )}
                <VerticalStack gap="2">
                    <Checkbox
                        label="Flag already expired tokens in active use"
                        checked={flagExpired}
                        onChange={setFlagExpired}
                    />
                    <Box paddingInlineStart="6">
                        <Text variant="bodySm" color="subdued">
                            Detect API calls or actions performed by an agent using an expired token, which may indicate a misconfiguration or security risk.
                        </Text>
                    </Box>
                </VerticalStack>
            </VerticalStack>
        </VerticalStack>
    );
}

// ── Main page ──────────────────────────────────────────────────────────────────
export default function CreateNhiPolicyModal({ onClose, onSave, onDisable, editingPolicy = null, isEditMode = false }) {
    const [currentStep, setCurrentStep]       = useState(1);
    const [completedSteps, setCompletedSteps] = useState([]);

    // Playground state
    const [playgroundInput, setPlaygroundInput]       = useState("");
    const [playgroundLoading, setPlaygroundLoading]   = useState(false);
    const [playgroundMessages, setPlaygroundMessages] = useState([]);
    const playgroundScrollRef = useRef(null);

    // Dynamic dropdown options fetched from nhi_identities
    const [agentOptions, setAgentOptions] = useState([]);
    const [nhiOptions, setNhiOptions]     = useState([]);

    // Step 1
    const [name, setName]                     = useState("");
    const [description, setDescription]       = useState("");
    const [selectedAgents, setSelectedAgents] = useState([]);
    const [selectedNhis, setSelectedNhis]     = useState([]);

    // Step 2
    const [tokenSegEnabled, setTokenSegEnabled] = useState(true);

    // Step 3
    const [expiryEnabled, setExpiryEnabled]       = useState(true);
    const [warningThreshold, setWarningThreshold] = useState(3);
    const [flagExpired, setFlagExpired]           = useState(true);

    useEffect(() => {
        document.body.classList.add('guardrail-page-open');
        return () => document.body.classList.remove('guardrail-page-open');
    }, []);

    // Auto-scroll playground to bottom on new messages
    useEffect(() => {
        if (playgroundScrollRef.current && playgroundMessages.length > 0) {
            playgroundScrollRef.current.scrollTop = playgroundScrollRef.current.scrollHeight;
        }
    }, [playgroundMessages]);

    // Fetch unique agent names and identity hexIds from nhi_identities
    useEffect(() => {
        const contextSource = IS_ARGUS ? "AGENTIC" : "ENDPOINT";
        observeRequests.fetchNhiIdentities(contextSource).then((identities) => {
            if (!Array.isArray(identities)) return;

            const agents = [...new Set(
                identities.map((i) => i.agentName).filter(Boolean)
            )].sort().map((n) => ({ label: n, value: n }));

            const nhis = identities
                .filter((i) => i.hexId && i.identityName)
                .map((i) => ({ label: i.identityName, value: i.hexId }))
                .sort((a, b) => a.label.localeCompare(b.label));

            setAgentOptions(agents);
            setNhiOptions(nhis);
        }).catch(() => {});
    }, []);

    // Pre-fill when editing
    useEffect(() => {
        if (isEditMode && editingPolicy) {
            populateFormForEdit(editingPolicy);
        } else {
            resetForm();
        }
    }, [isEditMode, editingPolicy]);

    const populateFormForEdit = (p) => {
        setName(p.policyName || "");
        setDescription(p.description || "");
        setSelectedAgents(p.scope?.agents || p.agents || []);
        setSelectedNhis(p.scope?.nhiIds || p.nhiIds || []);
        setTokenSegEnabled(p.tokenSegregation?.enabled ?? true);
        setExpiryEnabled(p.expirationTracking?.enabled ?? true);
        setWarningThreshold(p.expirationTracking?.warningThresholdMonths || 3);
        setFlagExpired(p.expirationTracking?.flagExpiredTokens ?? true);
        setCompletedSteps([1, 2]);
        setCurrentStep(1);
    };

    const resetForm = () => {
        setName(""); setDescription(""); setSelectedAgents([]); setSelectedNhis([]);
        setTokenSegEnabled(true); setExpiryEnabled(true); setWarningThreshold(3); setFlagExpired(true);
        setCompletedSteps([]); setCurrentStep(1);
        setPlaygroundMessages([]); setPlaygroundInput("");
    };

    const handleClose = () => { resetForm(); onClose(); };

    const buildPayload = (status) => ({
        policyName: name.trim() || "Untitled Policy",
        description,
        status: status || "ACTIVE",
        contextSource: IS_ARGUS ? "AGENTIC" : "ENDPOINT",
        scope: { agents: selectedAgents, nhiIds: selectedNhis },
        tokenSegregation: { enabled: tokenSegEnabled },
        expirationTracking: { enabled: expiryEnabled, warningThresholdMonths: warningThreshold, flagExpiredTokens: flagExpired },
    });

    const handleSubmit = (status) => {
        onSave(buildPayload(status), isEditMode ? editingPolicy?._id?.$oid || editingPolicy?.hexId : null);
        handleClose();
    };

    const goNext = () => {
        setCompletedSteps((prev) => prev.includes(currentStep) ? prev : [...prev, currentStep]);
        setCurrentStep((s) => Math.min(s + 1, STEPS.length));
    };

    const goPrev = () => setCurrentStep((s) => Math.max(s - 1, 1));

    const isLastStep = currentStep === STEPS.length;

    const handleQuickPromptClick = (prompt) => {
        if (playgroundLoading) return;
        setPlaygroundInput(prompt);
        setTimeout(() => {
            const el = document.querySelector('.playground-input-wrapper input, .playground-input-wrapper textarea');
            if (el) el.focus();
        }, 0);
    };

    const handlePlaygroundSubmit = async () => {
        if (!playgroundInput.trim() || playgroundLoading) return;
        const input = playgroundInput;
        setPlaygroundInput("");
        setPlaygroundLoading(true);
        // Placeholder: echo back — replace with real API call when backend is ready
        setPlaygroundMessages((prev) => [...prev, { userPrompt: input, action: "Received", message: "Ask Akto feature coming soon." }]);
        setPlaygroundLoading(false);
    };

    return (
        <div className="guardrail-page-wrapper">
            {/* Header */}
            <div className="guardrail-page-header">
                <HorizontalStack gap="2" blockAlign="center">
                    <Icon source={SettingsMajor} />
                    <Text variant="headingLg" as="h1">
                        {isEditMode ? "Edit Policy" : "Create Policy"}
                    </Text>
                </HorizontalStack>
                <button className="Polaris-Modal-CloseButton" onClick={handleClose}>
                    <Icon source={CancelMajor} />
                </button>
            </div>

            {/* Three-panel body */}
            <div className="guardrail-page-container">

                {/* Left sidebar — step navigator */}
                <div className="guardrail-sidebar">
                    <Box padding="5" paddingBlockEnd="4">
                        <Text variant="headingMd" as="h3" fontWeight="semibold">Policy Categories</Text>
                    </Box>
                    <Box paddingInline="2">
                        <VerticalStack gap="0">
                            {STEPS.map((step) => {
                                const isCompleted = completedSteps.includes(step.id);
                                const isActive    = currentStep === step.id;
                                return (
                                    <div
                                        key={step.id}
                                        className={`guardrail-nav-item ${isActive ? "active" : ""}`}
                                        onClick={() => isCompleted && setCurrentStep(step.id)}
                                        data-completed={isCompleted}
                                    >
                                        <div className={`step-indicator ${
                                            isActive     ? "current"    :
                                            isCompleted  ? "configured" : "pending"
                                        }`} />
                                        <Text
                                            variant="bodyMd"
                                            fontWeight={isActive ? "semibold" : "regular"}
                                        >
                                            {step.label}
                                        </Text>
                                    </div>
                                );
                            })}
                        </VerticalStack>
                    </Box>
                </div>

                {/* Center — step content */}
                <div className="guardrail-content">
                    <div className="guardrail-content-inner">
                        <Box padding="5">
                            <VerticalStack gap="4">
                                <Text variant="headingMd" as="h2" fontWeight="semibold">
                                    {STEPS.find((s) => s.id === currentStep)?.label}
                                </Text>
                                <Box>
                                    {currentStep === 1 && (
                                        <PolicyDetailsStep
                                            name={name} setName={setName}
                                            description={description} setDescription={setDescription}
                                            selectedAgents={selectedAgents} setSelectedAgents={setSelectedAgents}
                                            selectedNhis={selectedNhis} setSelectedNhis={setSelectedNhis}
                                            agentOptions={agentOptions}
                                            nhiOptions={nhiOptions}
                                        />
                                    )}
                                    {currentStep === 2 && (
                                        <TokenSegregationStep
                                            tokenSegEnabled={tokenSegEnabled}
                                            setTokenSegEnabled={setTokenSegEnabled}
                                        />
                                    )}
                                    {currentStep === 3 && (
                                        <ExpirationTrackingStep
                                            expiryEnabled={expiryEnabled} setExpiryEnabled={setExpiryEnabled}
                                            warningThreshold={warningThreshold} setWarningThreshold={setWarningThreshold}
                                            flagExpired={flagExpired} setFlagExpired={setFlagExpired}
                                        />
                                    )}
                                </Box>
                            </VerticalStack>
                        </Box>
                    </div>

                    {/* Footer */}
                    <div className="guardrail-content-footer">
                        <HorizontalStack align="space-between" blockAlign="center">
                            <HorizontalStack gap="2">
                                {isEditMode && onDisable && (
                                    <Button destructive onClick={onDisable}>Disable Policy</Button>
                                )}
                                <Button onClick={goPrev} disabled={currentStep === 1}>Previous</Button>
                            </HorizontalStack>
                            <HorizontalStack gap="2">
                                <Button onClick={goNext} disabled={isLastStep}>Next</Button>
                                <Button primary onClick={() => handleSubmit("ACTIVE")}>
                                    {isEditMode ? "Update policy" : "Create policy"}
                                </Button>
                            </HorizontalStack>
                        </HorizontalStack>
                    </div>
                </div>

                {/* Right panel — Ask Akto playground */}
                <div className="guardrail-playground">
                    <Box padding="5" paddingBlockEnd="4">
                        <Text variant="headingMd" as="h3" fontWeight="semibold">Ask Akto</Text>
                    </Box>

                    {/* Messages area */}
                    <div
                        ref={playgroundScrollRef}
                        style={{
                            flex: 1,
                            overflowY: "auto",
                            padding: "0 20px 20px",
                            minHeight: 0,
                            display: "flex",
                            flexDirection: "column",
                            ...(playgroundMessages.length === 0 ? { justifyContent: "center", alignItems: "center" } : {}),
                        }}
                    >
                        {playgroundMessages.length === 0 ? (
                            <Text variant="bodyMd" color="subdued" alignment="center">
                                Help me build this policy based on my NHI.
                            </Text>
                        ) : (
                            <VerticalStack gap="5">
                                {playgroundMessages.map((msg, i) => (
                                    <VerticalStack key={i} gap="3">
                                        <HorizontalStack align="end">
                                            <Box
                                                maxWidth="70%"
                                                padding="3"
                                                paddingInline="4"
                                                borderWidth="1"
                                                borderColor="border"
                                                background="bg-surface"
                                                borderRadius="3"
                                                borderRadiusStartEnd="1"
                                            >
                                                <Text variant="bodyMd">{msg.userPrompt}</Text>
                                            </Box>
                                        </HorizontalStack>
                                        <HorizontalStack align="start">
                                            <Box
                                                maxWidth="70%"
                                                padding="3"
                                                paddingInline="4"
                                                background="bg-surface"
                                                borderWidth="1"
                                                borderColor="border"
                                                borderRadius="3"
                                                borderRadiusEndStart="1"
                                            >
                                                <Text variant="bodyMd">{msg.message}</Text>
                                            </Box>
                                        </HorizontalStack>
                                    </VerticalStack>
                                ))}
                            </VerticalStack>
                        )}
                    </div>

                    {/* Input area */}
                    <div className="playground-input-wrapper">
                        <Box paddingBlockEnd="3">
                            <VerticalStack gap="2">
                                <Text variant="bodySm" color="subdued" fontWeight="medium">Quick Prompts</Text>
                                <VerticalStack gap="2">
                                    {QUICK_PROMPTS.map((prompt, i) => (
                                        <Box
                                            key={i}
                                            as="span"
                                            display="inlineBlock"
                                            onClick={() => handleQuickPromptClick(prompt)}
                                            style={{ cursor: playgroundLoading ? "not-allowed" : "pointer", opacity: playgroundLoading ? 0.5 : 1 }}
                                        >
                                            <Box
                                                as="span"
                                                paddingInlineStart="3"
                                                paddingInlineEnd="3"
                                                paddingBlockStart="1"
                                                paddingBlockEnd="1"
                                                borderRadius="2"
                                                borderWidth="1"
                                                borderColor="border"
                                                background="transparent"
                                            >
                                                <Text variant="bodySm" color="subdued" as="span" fontWeight="regular">
                                                    {prompt}
                                                </Text>
                                            </Box>
                                        </Box>
                                    ))}
                                </VerticalStack>
                            </VerticalStack>
                        </Box>
                        <AgenticSearchInput
                            value={playgroundInput}
                            onChange={setPlaygroundInput}
                            onSubmit={handlePlaygroundSubmit}
                            placeholder="Ask a follow up..."
                            isStreaming={playgroundLoading}
                        />
                    </div>
                </div>

            </div>
        </div>
    );
}
