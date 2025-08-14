import { Box, Button, HorizontalStack, LegacyCard } from "@shopify/polaris";
import { useEffect, useState } from "react";
import SampleData from "../../../components/shared/SampleData";
import api from "../api"
import func from '@/util/func';
import DropdownSearch from "../../../components/shared/DropdownSearch";
import { useSearchParams } from "react-router-dom";
import { getDashboardCategory } from "../../../../main/labelHelper";

function FilterComponent() {
    const[searchParams] = useSearchParams()
    const filteredPolicy = searchParams.get("policy")
    const [ogData, setOgData] = useState({ message: "" })
    const [data, setData] = useState({ message: "" })
    const [allData, setAllData] = useState([])
    const [id, setId] = useState("")
    const shortHand = getDashboardCategory().split(" ")[0].toLowerCase();
    const fetchData = async () => {
        await api.fetchFilterYamlTemplate().then((resp) => {
            let temp = resp?.templates ? resp?.templates : []
            if(!shortHand.includes("api")){  
                temp = temp.filter(x => x?.info?.category?.name !== undefined && x?.info?.category?.name?.toLowerCase().includes(shortHand))
            }else{
                temp = temp.filter(x => x?.info?.category?.name !== undefined && !x?.info?.category?.name?.toLowerCase().includes("mcp"))
            }
            setAllData(temp)
            if (temp.length > 0) {
                const temp2 = temp[0]
                if(filteredPolicy && filteredPolicy.length > 0){
                    setId(filteredPolicy)
                    try{
                        let content = temp.filter((x) => x.id === filteredPolicy)[0]?.content
                        setData({message: content})
                        setOgData({message: content})
                    }catch(err){
                        setId(temp2.id)
                        const temp3 = { message: temp2.content }
                        setData(temp3)
                        setOgData(temp3)
                    }
                    
                }else{
                    setId(temp2.id)
                    const temp3 = { message: temp2.content }
                    setData(temp3)
                    setOgData(temp3)
                }
            }
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
                <SampleData data={ogData} editorLanguage="custom_yaml" minHeight="65vh" readOnly={false} getEditorData={setData} />
            </LegacyCard.Section>
        </LegacyCard>
    )

}

export default FilterComponent;