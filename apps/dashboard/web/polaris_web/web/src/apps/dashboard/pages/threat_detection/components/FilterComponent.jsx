import { Box, Button, HorizontalStack, LegacyCard, Text } from "@shopify/polaris";
import { useEffect, useState } from "react";
import SampleData from "../../../components/shared/SampleData";
import api from "../api"
import func from '@/util/func';

function FilterComponent() {
    const [ogData, setOgData] = useState({ message: "" })
    const [data, setData] = useState({ message: "" })
    const fetchData = async () => {
        await api.fetchFilterYamlTemplate().then((resp) => {
            let temp = { message: "" }
            temp["message"] = resp?.template?.content == undefined ? "" : resp?.template?.content;
            setOgData(temp)
            setData(temp)
        });
    }
    useEffect(() => {
        fetchData();
    }, [])

    async function onSave() {
        await api.saveFilterYamlTemplate(data)
        func.setToast(true, false, 'Saved filter template')
    }

    return (
        <LegacyCard>
            <LegacyCard.Section flush>
                <Box padding={"2"}>
                    <HorizontalStack padding="2" align='space-between'>
                        Threat detection filter
                        <Button plain monochrome removeUnderline onClick={onSave}>
                            Save
                        </Button>
                    </HorizontalStack>
                </Box>
            </LegacyCard.Section>
            <LegacyCard.Section flush>
                <SampleData data={ogData} editorLanguage="custom_yaml" minHeight="240px" readOnly={false} getEditorData={setData} />
            </LegacyCard.Section>
        </LegacyCard>
    )

}

export default FilterComponent;