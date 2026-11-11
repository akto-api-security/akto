import { useEffect, useState } from "react"
import { Banner, Box, Button, Checkbox, DropZone, HorizontalStack, Icon, LegacyCard, Text, TextField, VerticalStack } from "@shopify/polaris"
import { DeleteMinor, NoteMinor } from "@shopify/polaris-icons"
import { useNavigate, useSearchParams } from "react-router-dom"
import DetailsPage from "../../../components/DetailsPage"
import DropdownSearch from "../../../components/shared/DropdownSearch"
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import { usePermissions } from "@/util/permissions"
import func from "@/util/func"
import api from "../api"
import { BUILT_IN_SKILLS, KNOWLEDGE_FILE_TYPES, LIMITS, emptyProfile, fromForm, toForm, validate } from "./smartAgentUtils"

function SmartAgentDetails() {
    const navigate = useNavigate()
    const [searchParams] = useSearchParams()
    const hexId = searchParams.get("id")
    const isNew = !hexId
    const { canCall } = usePermissions()
    const canSave = canCall("api/saveSmartAgentProfile")

    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [initial, setInitial] = useState({ ...emptyProfile })
    const [form, setForm] = useState({ ...emptyProfile })

    const set = (key) => (value) => setForm(prev => ({ ...prev, [key]: value }))

    useEffect(() => {
        async function load() {
            setLoading(true)
            try {
                const loaded = isNew ? { ...emptyProfile } : toForm((await api.fetchSmartAgentProfile(hexId))?.smartAgentProfile)
                setInitial(loaded)
                setForm(loaded)
            } catch (e) {
                func.setToast(true, true, "Unable to load the smart agent")
            } finally {
                setLoading(false)
            }
        }
        load()
    }, [hexId])

    const changed = JSON.stringify(form) !== JSON.stringify(initial)

    async function saveAction() {
        const problem = validate(form)
        if (problem) {
            func.setToast(true, true, problem)
            return
        }
        setSaving(true)
        try {
            const res = await api.saveSmartAgentProfile(fromForm(form))
            const saved = toForm(res?.smartAgentProfile)
            setInitial(saved)
            setForm(saved)
            func.setToast(true, false, isNew ? "Smart agent created" : "Smart agent saved")
            if (isNew && saved.hexId) {
                navigate(`/dashboard/testing/smart-agents/details?id=${saved.hexId}`, { replace: true })
            }
        } catch (e) {
            // the request layer shows the API's message
        } finally {
            setSaving(false)
        }
    }

    // custom skills
    const addCustomSkill = () => setForm(prev => ({ ...prev, customSkills: [...prev.customSkills, { name: "", content: "" }] }))
    const setCustomSkill = (index, key) => (value) => setForm(prev => ({
        ...prev, customSkills: prev.customSkills.map((skill, i) => i === index ? { ...skill, [key]: value } : skill),
    }))
    const removeCustomSkill = (index) => setForm(prev => ({ ...prev, customSkills: prev.customSkills.filter((_, i) => i !== index) }))

    // knowledge base files
    async function addFiles(_dropFiles, acceptedFiles, rejectedFiles) {
        if (rejectedFiles?.length) {
            func.setToast(true, true, `Only text files can be added (${KNOWLEDGE_FILE_TYPES})`)
        }
        const room = LIMITS.files - form.knowledgeFiles.length
        const added = []
        for (const file of acceptedFiles.slice(0, Math.max(room, 0))) {
            const content = await file.text()
            if (content.length > LIMITS.fileChars) {
                func.setToast(true, true, `${file.name} is too large (at most ${LIMITS.fileChars} characters of text)`)
                continue
            }
            added.push({ name: file.name, content, size: content.length })
        }
        if (added.length) {
            setForm(prev => ({ ...prev, knowledgeFiles: [...prev.knowledgeFiles.filter(f => !added.some(a => a.name === f.name)), ...added] }))
        }
    }
    const removeFile = (name) => setForm(prev => ({ ...prev, knowledgeFiles: prev.knowledgeFiles.filter(f => f.name !== name) }))

    if (loading) {
        return <SpinnerCentered />
    }

    const agentCard = (
        <LegacyCard title="Agent" key="agent" sectioned>
            <VerticalStack gap="4">
                <TextField label="Name" value={form.name} onChange={set("name")} maxLength={LIMITS.name} autoComplete="off"
                    placeholder="e.g. Payments API agent" requiredIndicator disabled={!canSave} />
                <TextField label="Description" value={form.description} onChange={set("description")} maxLength={LIMITS.description}
                    autoComplete="off" placeholder="What this agent is for" disabled={!canSave} />
            </VerticalStack>
        </LegacyCard>
    )

    const skillsCard = (
        <LegacyCard title="Skills" key="skills" sectioned>
            <VerticalStack gap="4">
                <Box width="350px">
                    <DropdownSearch
                        id="smart-agent-skills"
                        label="Skills"
                        placeholder="Select skills"
                        optionsList={BUILT_IN_SKILLS.map(skill => ({ label: skill.label, value: skill.id }))}
                        setSelected={set("skills")}
                        preSelected={form.skills}
                        value={form.skills.length ? `${form.skills.length} skill${form.skills.length > 1 ? "s" : ""} selected` : ""}
                        itemName="skill"
                        allowMultiple
                        disabled={!canSave}
                    />
                </Box>
                {form.skills.length > 0 ? (
                    <VerticalStack gap="2">
                        {BUILT_IN_SKILLS.filter(skill => form.skills.includes(skill.id)).map(skill => (
                            <Box key={skill.id} padding="3" borderWidth="1" borderColor="border-subdued" borderRadius="2">
                                <HorizontalStack align="space-between" blockAlign="start" wrap={false} gap="4">
                                    <VerticalStack gap="1">
                                        <Text fontWeight="semibold">{skill.label}</Text>
                                        <Text color="subdued">{skill.description}</Text>
                                    </VerticalStack>
                                    {canSave ? <Button plain destructive icon={DeleteMinor} accessibilityLabel={`Remove skill ${skill.label}`}
                                        onClick={() => setForm(prev => ({ ...prev, skills: prev.skills.filter(id => id !== skill.id) }))} /> : null}
                                </HorizontalStack>
                            </Box>
                        ))}
                    </VerticalStack>
                ) : <Text color="subdued">No built-in skill selected.</Text>}
                <VerticalStack gap="3">
                    <Text fontWeight="medium">Custom skills</Text>
                    {form.customSkills.map((skill, index) => (
                        <Box key={index} padding="3" borderWidth="1" borderColor="border-subdued" borderRadius="2">
                            <VerticalStack gap="2">
                                <HorizontalStack align="space-between" blockAlign="center">
                                    <Box width="70%">
                                        <TextField label="Skill name" labelHidden placeholder="Skill name, e.g. Refund abuse" value={skill.name}
                                            onChange={setCustomSkill(index, "name")} maxLength={LIMITS.name} autoComplete="off" disabled={!canSave} />
                                    </Box>
                                    {canSave ? <Button plain destructive icon={DeleteMinor} accessibilityLabel={`Remove custom skill ${skill.name}`} onClick={() => removeCustomSkill(index)} /> : null}
                                </HorizontalStack>
                                <TextField label="What the skill does" labelHidden multiline={3} value={skill.content}
                                    onChange={setCustomSkill(index, "content")} maxLength={LIMITS.customSkill} autoComplete="off"
                                    placeholder="What the agent should try and what counts as a finding, e.g. refund the same order twice and check the balance"
                                    disabled={!canSave} />
                            </VerticalStack>
                        </Box>
                    ))}
                    {canSave ? <Box><Button onClick={addCustomSkill}>Add custom skill</Button></Box> : null}
                </VerticalStack>
            </VerticalStack>
        </LegacyCard>
    )

    const identitiesCard = (
        <LegacyCard title="Identities" key="identities" sectioned>
            <VerticalStack gap="3">
                <Text color="subdued">The agent tests as the test role chosen when the test is run.</Text>
                <Checkbox
                    label="Let the agent create its own users or API keys at other role levels when it needs them"
                    helpText="Use on test environments only: the agent creates them through the API under test and removes what it created at the end."
                    checked={form.allowCreateIdentities}
                    onChange={set("allowCreateIdentities")}
                    disabled={!canSave}
                />
            </VerticalStack>
        </LegacyCard>
    )

    const knowledgeBaseCard = (
        <LegacyCard title="Knowledge base" key="knowledge-base" sectioned>
            <VerticalStack gap="4">
                <TextField label="Instructions" value={form.instructions} onChange={set("instructions")} multiline={4}
                    maxLength={LIMITS.instructions} showCharacterCount autoComplete="off"
                    placeholder="How the API works and what to use: roles, who owns which objects, ids and values to use, what never to change"
                    disabled={!canSave} />
                <VerticalStack gap="2">
                    <Text fontWeight="medium">Files</Text>
                    {form.knowledgeFiles.map(file => (
                        <HorizontalStack key={file.name} align="space-between" blockAlign="center">
                            <HorizontalStack gap="2" blockAlign="center">
                                <Box><Icon source={NoteMinor} color="subdued" /></Box>
                                <Text>{file.name}</Text>
                                <Text color="subdued">{`${Math.max(1, Math.ceil((file.size || 0) / 1000))}k characters`}</Text>
                            </HorizontalStack>
                            {canSave ? <Button plain destructive icon={DeleteMinor} accessibilityLabel={`Remove ${file.name}`} onClick={() => removeFile(file.name)} /> : null}
                        </HorizontalStack>
                    ))}
                    {canSave && form.knowledgeFiles.length < LIMITS.files ? (
                        <DropZone accept={KNOWLEDGE_FILE_TYPES} type="file" allowMultiple onDrop={addFiles} accessibilityLabel="Add files">
                            <DropZone.FileUpload actionTitle="Add files" actionHint={`Text files (${KNOWLEDGE_FILE_TYPES}), up to ${LIMITS.files}`} />
                        </DropZone>
                    ) : null}
                </VerticalStack>
                <TextField label="Links" value={form.knowledgeLinksText} onChange={set("knowledgeLinksText")} multiline={3}
                    autoComplete="off" placeholder={"One link per line, e.g.\nhttps://api.example.com/openapi.json\nhttps://docs.example.com/guides/roles"}
                    helpText={`API docs and other pages; an OpenAPI spec link is used as the API's documentation. At most ${LIMITS.links} links.`}
                    disabled={!canSave} />
            </VerticalStack>
        </LegacyCard>
    )

    const banner = !canSave ? <Banner key="read-only" status="info">Read-only: your role can't change smart agents.</Banner> : null

    return (
        <DetailsPage
            pageTitle={isNew ? "New smart agent" : (initial.name || "Smart agent")}
            backUrl="/dashboard/testing/smart-agents"
            saveAction={saveAction}
            discardAction={() => setForm(initial)}
            isDisabled={() => !changed || !canSave}
            isSaving={saving}
            components={[banner, agentCard, skillsCard, identitiesCard, knowledgeBaseCard].filter(Boolean)}
        />
    )
}

export default SmartAgentDetails
