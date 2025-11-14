import { Box, Button, HorizontalStack, LegacyCard } from "@shopify/polaris";
import { useEffect, useState } from "react";
import SampleData from "../../../components/shared/SampleData";
import api from "../api"
import func from '@/util/func';
import DropdownSearch from "../../../components/shared/DropdownSearch";
import { useSearchParams } from "react-router-dom";
import { updateThreatFiltersStore } from "../utils/threatFilters";

function FilterComponent({ includeCategoryNameEquals, excludeCategoryNameEquals, titleText, readOnly = false, validateOnSave, showDelete = false }) {
    const[searchParams] = useSearchParams()
    const filteredPolicy = searchParams.get("policy")
    const [ogData, setOgData] = useState({ message: "" })
    const [data, setData] = useState({ message: "" })
    const [allData, setAllData] = useState([])
    const [id, setId] = useState("")
    // helpers: normalize and category accessor
    const normalize = (s) => (s || '').toLowerCase()
    const getCategory = (t) => normalize(t?.info?.category?.name)

    const fetchTemplates = async () => {
        const resp = await api.fetchFilterYamlTemplate()
        const templates = Array.isArray(resp?.templates) ? resp.templates : []
        let categoryFilteredTemplates = templates;
        try {
            categoryFilteredTemplates = updateThreatFiltersStore(templates)
        } catch (e) {
            console.error(`Failed to update SessionStore threat filters: ${e?.message}`);
        }

        return categoryFilteredTemplates
    }

    const filterTemplates = (templates) => {
        let out = templates

        // include/exclude by category name
        if (includeCategoryNameEquals && includeCategoryNameEquals.length > 0) {
            const inc = normalize(includeCategoryNameEquals)
            out = out.filter((t) => getCategory(t) === inc)
        } else if (excludeCategoryNameEquals && excludeCategoryNameEquals.length > 0) {
            const exc = normalize(excludeCategoryNameEquals)
            out = out.filter((t) => getCategory(t) !== exc)
        }
        return out
    }

    const pickTemplate = (templates) => {
        if (!templates || templates.length === 0) return { id: undefined, content: '' }
        if (filteredPolicy && filteredPolicy.length > 0) {
            const chosen = templates.find((t) => t.id === filteredPolicy) || templates[0]
            return { id: chosen?.id, content: chosen?.content || '' }
        }
        const first = templates[0]
        return { id: first?.id, content: first?.content || '' }
    }

    const applySelection = ({ id, content }) => {
        if (id === undefined) return
        setId(id)
        const payload = { message: content }
        setData(payload)
        setOgData(payload)
    }

    const fetchData = async () => {
        try {
            const templates = await fetchTemplates()
            const filtered = filterTemplates(templates)
            setAllData(filtered)
            if (filtered.length === 0) return
            const selection = pickTemplate(filtered)
            applySelection(selection)
        } catch (e) {
            console.error(`Failed to fetch threat policies error: ${e?.message}`);
        }
    }
    useEffect(() => {
        fetchData();
    }, [])

    async function onSave() {
        // Run validation if provided
        if (validateOnSave) {
            const validationResult = validateOnSave(data);
            if (!validationResult.isValid) {
                func.setToast(true, true, validationResult.errorMessage);
                return;
            }
        }

        await api.saveFilterYamlTemplate(data)
        func.setToast(true, false, 'Saved filter template')
    }

    async function onDelete() {
        try {
            if (!id) {
                func.setToast(true, true, 'No policy selected to delete')
                return
            }
            await api.deleteFilterYamlTemplate(id)
            func.setToast(true, false, 'Deleted filter template')
            await fetchData()
        } catch (e) {
            func.setToast(true, true, 'Failed to delete filter template')
        }
    }

    async function onDeleteClick() {
        if (!id) {
            func.setToast(true, true, 'No policy selected to delete')
            return
        }
        func.showConfirmationModal(
            'Are you sure you want to delete this filter template? This action cannot be undone.',
            'Delete',
            async () => {
                await onDelete()
            }
        )
    }

    return (
        <LegacyCard>
            <LegacyCard.Section flush>
                <Box padding={"2"}>
                    <HorizontalStack padding="2" align='space-between'>
                        {titleText ? titleText : 'Threat detection filter'}
                        {!readOnly && (
                            <HorizontalStack gap="2">
                                {showDelete && (
                                    <Button outline size="slim" onClick={onDeleteClick}>
                                        Delete
                                    </Button>
                                )}
                                <Button outline size="slim" onClick={onSave}>
                                    Save
                                </Button>
                            </HorizontalStack>
                        )}
                    </HorizontalStack>
                </Box>
            </LegacyCard.Section>
            <LegacyCard.Section>
                <DropdownSearch
                    placeholder={"Search filters"}
                    optionsList={allData.map(x => {
                        return {
                            label: x.id,
                            value: x.id
                        }
                    })}
                    setSelected={(value) => {
                        let content = allData.filter(x =>
                            x.id == value
                        )[0].content;
                        let temp = { message: content }
                        setId(value)
                        setData(temp)
                        setOgData(temp)
                    }}
                    preSelected={[
                        id
                    ]}
                    value={id}
                />
            </LegacyCard.Section>
            <LegacyCard.Section flush>
                <SampleData data={ogData} editorLanguage="custom_yaml" minHeight="65vh" readOnly={readOnly} getEditorData={setData} />
            </LegacyCard.Section>
        </LegacyCard>
    )

}

export default FilterComponent;