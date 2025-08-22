import React, { useEffect, useState } from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import { Box, Button, Checkbox, HorizontalStack, Icon, LegacyCard, Modal, Popover, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import trafficFiltersRequest from './api'
import func from "@/util/func"
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import DropdownSearch from '../../../components/shared/DropdownSearch'
import SampleData from '../../../components/shared/SampleData'
import { DeleteMajor, CircleCancelMajor, CircleTickMajor } from "@shopify/polaris-icons"

function AdvancedTrafficFilters() {
    const [topLevelActive, setTopLevelActive] = useState(false);
    const [deleteSlash, setDeleteSlash] = useState(false)
    function MainComp () {
        const [currentTemplate, setCurrentTemplate] = useState({message: ""})
        const [ogData, setOgData] = useState({ message: "" })
        const [templateList, setTemplateList] = useState([])
        const [currentId, setCurrentId] = useState("")
        const [currentState, setCurrentState] = useState(false)
        const [modalActive, setModalActive] = useState(false);
        const [popoverActive, setPopoverActive] = useState(false);
        const [permissionsMap,setPermissionsMap] = useState({})

        const fetchData = async () => {
            await trafficFiltersRequest.getAdvancedFiltersForTraffic().then((resp) => {
                if(resp !== null && resp.length > 0) {
                    setTemplateList(resp)
                    const currentObj = resp[0];
                    setCurrentState(currentObj?.inactive)
                    setCurrentId(currentObj.id)
                    const template = {message: currentObj.content}
                    setCurrentTemplate(template)
                    setOgData(template)
                }
                
            })
            await trafficFiltersRequest.getAdvancedFiltersPermissions().then((resp) => {
                setPermissionsMap(resp)
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

        const handleDryRun = async(content, shouldDelete) => {
            if(window.IS_SAAS !== "true" ||  window.USER_NAME.includes("akto")){
                if(topLevelActive) {
                    await trafficFiltersRequest.cleanUpInventory(shouldDelete).then((res)=> {
                        console.log("Clean up inventory response", res)
                    })
                }else if(deleteSlash) {
                    await trafficFiltersRequest.deleteOptionAndSlashApis(shouldDelete).then((res)=> {
                        console.log("Delete options and slash APIs response", res)
                    })
                }else{
                    await trafficFiltersRequest.dryRunAdvancedFilters(content, shouldDelete).then((res)=> {
                        window.open("/dashboard/settings/logs", "_blank")
                    })
                }
                
            }
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
            await trafficFiltersRequest.changeStateOfFilter(currentId, !currentState)
            setCurrentState(!currentState)
            fetchData()
        }

        const handleSelection = (value) => {
            let state = false
            let contentObj = templateList.filter(x =>
                x.id === value
            )[0];
            let content = contentObj.content
            if(contentObj?.inactive !== undefined){
                state = contentObj?.inactive
            }
            setCurrentId(value)
            const temp = {message: content}
            setOgData(temp)
            setCurrentTemplate(temp)
            setCurrentState(state)
        }

        const handleCheckboxClicked = async(permission, value) => {
            await trafficFiltersRequest.updateAdvancedFiltersPermissions(value, permission).then((res) => {
                setPermissionsMap((prev) => {
                    prev[permission] = value
                    return {...prev}
                })
            })
        }

        const tooltipText= currentState ? "Mark as Active" : "Mark as Deactive"

        const titleComp = (
            <HorizontalStack align="space-between">
                <Text variant="headingSm">Add or modify filters</Text>
                <Popover
                    activator={<Button disclosure size="slim" onClick={() => setPopoverActive(!popoverActive)}>Actions</Button>}
                    active={popoverActive}
                    onClose={() => setPopoverActive(false)}
                    autofocusTarget="container"
                >
                    <Popover.Section>
                        <VerticalStack gap={"2"}>
                            <Checkbox
                                checked={permissionsMap['allowFilterLogs']}
                                label="Allow filtered urls in logs"
                                onChange={() => handleCheckboxClicked('allowFilterLogs', !permissionsMap['allowFilterLogs'])}
                            />
                            <Checkbox
                                label="Allow retrospective deletion"
                                checked={permissionsMap['allowDeletionOfUrls']}
                                onChange={() => handleCheckboxClicked('allowDeletionOfUrls', !permissionsMap['allowDeletionOfUrls'])}
                            />
                        </VerticalStack>
                    </Popover.Section>
                </Popover>
            </HorizontalStack>
        )

        return(
            <>
            <LegacyCard 
                title={titleComp} 
                footerActionAlignment="right"
                primaryFooterAction={{content: 'Save', onAction: () => setModalActive(true), 
                    disabled: (currentTemplate?.message !== undefined && currentTemplate.message.length === 0) || (typeof (currentTemplate) === 'string' && currentTemplate.length === 0)
                }}
                secondaryFooterActions={[{content: 'Add new', onAction: () => resetFunc()}]}
            >
                <LegacyCard.Section>
                    <DropdownSearch
                        placeholder={"Search existing filters"}
                        optionsList={templateList && templateList.map(x => {
                            return {
                                label: x.id === "DEFAULT_BLOCK_FILTER" ? "DEFAULT_IGNORE_FILTER" : x.id,
                                value: x.id
                            }
                        })}
                        setSelected={(value) => {
                            handleSelection(value)
                        }}
                        value={currentId === "DEFAULT_BLOCK_FILTER" ? "DEFAULT_IGNORE_FILTER" : currentId }
                    />
                </LegacyCard.Section>
                <LegacyCard.Section flush>
                    <Box paddingBlockStart={4} paddingBlockEnd={4}>
                        <VerticalStack gap={"1"}>
                            <Box paddingInlineEnd={"4"}>
                                <HorizontalStack align="end" gap={"2"}>
                                    <Button plain monochrome disabled={currentId.length === 0} onClick={() => changeStateOfFilter()}>
                                        <Tooltip content={tooltipText} dismissOnMouseOut>
                                            <Box><Icon source={currentState ? CircleTickMajor : CircleCancelMajor} /></Box>
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
            <Modal
                open={modalActive || topLevelActive || deleteSlash}
                onClose={() => {setModalActive(false); setTopLevelActive(false); setDeleteSlash(false)}}
                primaryAction={{content: 'Save', onAction: () => {handleSave(currentTemplate); setModalActive(false)} , disabled: topLevelActive || deleteSlash}}
                secondaryActions={(window.IS_SAAS !== "true" ||  window.USER_NAME.includes("akto"))? [{content: 'Dry run', onAction: () => handleDryRun(currentTemplate, false)},{content: 'Delete APIs matched', onAction: ()=> handleDryRun(currentTemplate, true) }]: []}
                title={"Add advanced filters"}
            >
                <Modal.Section>
                    <Text variant="bodyMd" color="subdued">
                        Adding an filter will stop/modify traffic ingestion in the dashboard.
                        Are you sure you want to add the filter?
                    </Text>
                </Modal.Section>
            </Modal>
            </>
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
            primaryAction={window.USER_NAME && window.USER_NAME.endsWith("@akto.io") ? <Button onClick={() => setTopLevelActive(true)}>Clean up</Button> : null}
            secondaryActions={window.USER_NAME && window.USER_NAME.endsWith("@akto.io") ? <Button onClick={() => setDeleteSlash(true)}>Delete Options & Slash APIs</Button> : null}
        />
    )
}

export default AdvancedTrafficFilters