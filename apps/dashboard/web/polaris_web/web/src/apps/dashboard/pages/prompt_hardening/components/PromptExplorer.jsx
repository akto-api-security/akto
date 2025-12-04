import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"

import { Box, Button, HorizontalStack, Icon, Navigation, Text, TextField, Tooltip, VerticalStack } from "@shopify/polaris"
import {ChevronDownMinor, ChevronRightMinor, SearchMinor} from "@shopify/polaris-icons"

import PromptHardeningStore from "../promptHardeningStore"
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"

import "../PromptHardening.css"

const PromptExplorer = ({addCustomPrompt}) => {

    const promptsObj = PromptHardeningStore(state => state.promptsObj)
    const selectedPrompt = PromptHardeningStore(state => state.selectedPrompt)
    const setSelectedPrompt = PromptHardeningStore(state => state.setSelectedPrompt)

    const [selectedCategory, setSelectedCategory] = useState('none')
    const [customItems, setCustomItems] = useState({items: [] , count : 0})
    const [aktoItems, setAktoItems] = useState({items: [] , count : 0})
    const [searchText, setSearchText] = useState('')
    const [showCustom, setShowCustom] = useState(false)
    const [showAkto, setShowAkto] = useState(false)

    const navigate = useNavigate()

    const selectedFunc = (val) =>{
        setSelectedCategory((prev) => {
            if(prev === val){
                return "none"
            }else{
                return val
            }
        })
    }

    const toggleFunc = (param) =>{
        if(param === 'CUSTOM'){
            if(showCustom){
                setShowCustom(false)
            }else{
                setShowCustom(true)
            }
            setShowAkto(false)
        }else{
            if(showAkto){
                setShowAkto(false)
            }else{
                setShowAkto(true)
            }
            setShowCustom(false)
        }
    }

    const searchFunction = (cloneObj, searchString) =>{
        let customTotal = 0
        let aktoTotal = 0

        for (let key in cloneObj.aktoPrompts) {
            if (cloneObj.aktoPrompts.hasOwnProperty(key)) {
                let obj = cloneObj.aktoPrompts[key]
                let arr = obj.filter((prompt)=>{
                    let name = prompt.label.toString().toLowerCase().replace(/ /g, "")
                    let category = prompt.category.toString().toLowerCase().replace(/ /g, "")
                    let content = cloneObj.mapPromptToData?.[prompt.label]?.content?.toString().toLowerCase() || "";
                    if(name.includes(searchString) || category.includes(searchString) || content.includes(searchString)){
                        aktoTotal++
                        return true
                    }
                })
                cloneObj.aktoPrompts[key] = arr
            }
        }

        for (let key in cloneObj.customPrompts) {
            if (cloneObj.customPrompts.hasOwnProperty(key)) {
                let obj = cloneObj.customPrompts[key]
                let arr = obj.filter((prompt)=>{
                    let name = prompt.label.toString().toLowerCase().replace(/ /g, "")
                    let category = prompt.category.toString().toLowerCase().replace(/ /g, "")
                    let content = cloneObj.mapPromptToData?.[prompt.label]?.content?.toString().toLowerCase() || "";
                    if(name.includes(searchString) || category.includes(searchString) || content.includes(searchString)){
                        customTotal++
                        return true
                    }
                })
                cloneObj.customPrompts[key] = arr
            }
        }

        cloneObj.totalCustomPrompts = customTotal
        cloneObj.totalAktoPrompts = aktoTotal
        return cloneObj
    }

    const searchResult = (val) => {
        let cloneObj = JSON.parse(JSON.stringify(promptsObj))
        setSearchText(val)
        let searchObj = searchFunction(cloneObj, val.toLowerCase())

        setCustomItems(getNavigationItems(searchObj,"CUSTOM",selectedFunc))
        setAktoItems(getNavigationItems(searchObj,"Akto",selectedFunc))
    }

    const getNavigationItems = (obj, type) => {
        const prompts = type === "CUSTOM" ? obj.customPrompts : obj.aktoPrompts
        const total = type === "CUSTOM" ? obj.totalCustomPrompts : obj.totalAktoPrompts
        
        let items = []
        for (let category in prompts) {
            if (prompts[category] && prompts[category].length > 0) {
                items.push({
                    key: category,
                    param: type === "CUSTOM" ? '_custom' : '_akto',
                    label: <Text variant="bodyMd">{category}</Text>,
                    url: '#',
                    onClick: () => selectedFunc(category + (type === "CUSTOM" ? '_custom' : '_akto')),
                    subNavigationItems: prompts[category]
                })
            }
        }
        
        return { items, count: total || 0 }
    }

    useEffect(()=>{
        setCustomItems(getNavigationItems(promptsObj,"CUSTOM",selectedFunc))
        setAktoItems(getNavigationItems(promptsObj,"Akto",selectedFunc))
    },[promptsObj])

    useEffect(() => {
        if (selectedPrompt) {
            // Determine if it's custom or akto prompt
            const isCustom = promptsObj.customPrompts?.[selectedPrompt.category]?.some(p => p.label === selectedPrompt.label)
            if(isCustom){
                setSelectedCategory(selectedPrompt.category + '_custom')
            }else{
                setSelectedCategory(selectedPrompt.category + '_akto')
            }
        }
    }, [selectedPrompt])

    useEffect(()=> {
        // Set initial toggle state based on selected prompt
        if (selectedPrompt) {
            const isCustom = promptsObj.customPrompts?.[selectedPrompt.category]?.some(p => p.label === selectedPrompt.label)
            toggleFunc(isCustom ? "CUSTOM" : "Akto")
        } else {
            toggleFunc("CUSTOM")
        }
    },[])

    function getItems(aktoItems){
        const arr = aktoItems.map(obj => ({
            ...obj,
            selected: selectedCategory === (obj.key+obj.param),
            icon: selectedCategory === (obj.key+obj.param) ? ChevronDownMinor : ChevronRightMinor,
            subNavigationItems: obj.subNavigationItems.map((item)=>{
                return{
                    label: (
                        <Tooltip content={item.label} dismissOnMouseOut width="wide" preferredPosition="below">
                            <div className={item.label === selectedPrompt?.label ? "active-left-test" : ""}>
                                <Text 
                                    variant={item.label === selectedPrompt?.label ? "headingSm" : "bodyMd"} as="h4" 
                                    color={item.label === selectedPrompt?.label ? "default" : "subdued"} truncate
                                >
                                    {item.label} 
                                </Text>
                            </div>
                        </Tooltip>
                    ),
                    onClick: (()=> {
                        navigate(`/dashboard/prompt-hardening/${item.value}`)
                        setSelectedPrompt(item)                        
                    }),
                    key: item.value
                }
            })
        }))
        return arr
    }
    
    return (
        <div className="editor-navbar" style={{'overflowY' : 'scroll', overflowX: 'hidden'} }>
            <Navigation location="/">
                <VerticalStack gap="4">
                    <TextField  
                        id={"prompt-search"}
                        prefix={<Icon source={SearchMinor} />} 
                        onChange={searchResult} 
                        value={searchText}
                        placeholder={`Search for Prompts`}
                    />

                    <Box>
                        <Button id={"create-custom-prompt-button"}
                            plain monochrome onClick={()=> toggleFunc("CUSTOM")} removeUnderline fullWidth
                        >
                            <HorizontalStack gap={"1"}>
                                <Box>
                                    <Icon source={showCustom ? ChevronDownMinor : ChevronRightMinor}/>
                                </Box>
                                <TitleWithInfo 
                                    tooltipContent={"Custom security prompts"} 
                                    titleText={"Custom"} 
                                    textProps={{variant: 'headingMd'}} 
                                    docsUrl={"https://docs.akto.io/prompt-playground/custom-prompts"}
                                />
                            </HorizontalStack>
                        </Button>
                        {showCustom ? <Navigation.Section items={getItems(customItems.items)} /> : null}
                    </Box>
                    <Box>
                        <Button plain monochrome onClick={() => toggleFunc("Akto")} removeUnderline fullWidth>
                            <HorizontalStack gap="1">
                                <Box>
                                    <Icon source={showAkto ? ChevronDownMinor : ChevronRightMinor}/>
                                </Box>
                                <TitleWithInfo 
                                    tooltipContent={"Akto's security prompt library"} 
                                    titleText={"Akto default"} 
                                    textProps={{variant: 'headingMd'}} 
                                    docsUrl={"https://docs.akto.io/prompt-playground/prompt-library"}
                                />
                            </HorizontalStack>
                        </Button>
                        {showAkto ? <Navigation.Section items={getItems(aktoItems.items)} /> : null}
                    </Box>
                </VerticalStack>
            </Navigation>
        </div>
    )
}

export default PromptExplorer