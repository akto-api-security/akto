import React, { useEffect, useState } from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import { Box, Button, HorizontalStack, Icon, LegacyCard, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import trafficFiltersRequest from './api'
import func from "@/util/func"
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import DropdownSearch from '../../../components/shared/DropdownSearch'
import SampleData from '../../../components/shared/SampleData'
import { DeleteMajor, CircleCancelMajor, CircleTickMajor } from "@shopify/polaris-icons"

function AdvancedTrafficFilters() {
    function MainComp () {
        const [currentTemplate, setCurrentTemplate] = useState({message: ""})
        const [ogData, setOgData] = useState({ message: "" })
        const [templateList, setTemplateList] = useState([])
        const [currentId, setCurrentId] = useState("")
        const [currentState, setCurrentState] = useState(true)

        const fetchData = async () => {
            await trafficFiltersRequest.getAdvancedFiltersForTraffic().then((resp) => {
                setTemplateList(resp || [])
            })
        }

        useEffect(() => {
            fetchData()
        },[])

        const handleSave = async(content) => {
            await trafficFiltersRequest.updateAdvancedFiltersForTraffic(content).then((resp) => {
                try {
                    func.setToast(true, false, "YAML template saved successfully");
                    fetchData()
                } catch (error) {
                    func.setToast(true, true, "Error saving template for filter");
                }
            })
        
        }

        const resetFunc = () => {
            setCurrentTemplate({message: ''});
            setOgData({message: ''}); 
            setCurrentId('')
        }

        const handleDelete = async() => {
            await trafficFiltersRequest.deleteFilter(currentId)
            resetFunc()
            fetchData();
        }

        const changeStateOfFilter = async() => {
            await trafficFiltersRequest.changeStateOfFilter(currentId, currentState)
            setCurrentState(!currentState)
            fetchData()
        }

        const handleSelection = (value) => {
            let state = true
            let contentObj = templateList.filter(x =>
                x.id === value
            )[0];
            let content = contentObj.content
            state = contentObj?.state || true
            setCurrentId(value)
            const temp = {message: content}
            setOgData(temp)
            setCurrentTemplate(temp)
            setCurrentState(!state)
        }

        const tooltipText= currentState ? "Mark as Deactive" : "Mark as Active"

        return(
            <LegacyCard 
                title={(<Text variant="headingSm">Add or modify filters</Text>)} 
                footerActionAlignment="right"
                primaryFooterAction={{content: 'Save', onAction: () => handleSave(currentTemplate), 
                    disabled: (currentTemplate?.message !== undefined && currentTemplate.message.length === 0) || (typeof (currentTemplate) === 'string' && currentTemplate.length === 0)
                }}
                secondaryFooterActions={[{content: 'Add new', onAction: () => resetFunc()}]}
            >
                <LegacyCard.Section>
                    <DropdownSearch
                        placeholder={"Search existing filters"}
                        optionsList={templateList && templateList.map(x => {
                            return {
                                label: x.id,
                                value: x.id
                            }
                        })}
                        setSelected={(value) => {
                            handleSelection(value)
                        }}
                        value={currentId}
                    />
                </LegacyCard.Section>
                <LegacyCard.Section flush>
                    <Box paddingBlockStart={4} paddingBlockEnd={4}>
                        <VerticalStack gap={"1"}>
                            <Box paddingInlineEnd={"4"}>
                                <HorizontalStack align="end" gap={"2"}>
                                    <Button plain monochrome disabled={currentId.length === 0} onClick={() => changeStateOfFilter()}>
                                        <Tooltip content={tooltipText} dismissOnMouseOut>
                                            <Box><Icon source={currentState === true ? CircleCancelMajor : CircleTickMajor} /></Box>
                                        </Tooltip>
                                    </Button>
                                    <Button plain destructive disabled={currentId.length === 0} onClick={() => handleDelete()}>
                                        <Tooltip content="Delete template" dismissOnMouseOut>
                                            <Box><Icon source={DeleteMajor} /></Box>
                                        </Tooltip>
                                    </Button>
                                </HorizontalStack>
                            </Box>
                            <SampleData data={ogData} editorLanguage="custom_yaml" minHeight="240px" readOnly={false} getEditorData={setCurrentTemplate} />
                        </VerticalStack>
                    </Box>
                </LegacyCard.Section>
            </LegacyCard>
        )
    }

    const components = [<MainComp key={"main-comp-filter"} />]

    return (
        <PageWithMultipleCards
            components={components}
            title={
                <TitleWithInfo
                    tooltipContent={"Add advanced options for filtering traffic captured by AKTO in the same format as YAML in test editor"}
                    titleText={"Advanced filter options"} 
                />
            }
            isFirstPage={true}
            divider={true}
            mor
        />
    )
}

export default AdvancedTrafficFilters