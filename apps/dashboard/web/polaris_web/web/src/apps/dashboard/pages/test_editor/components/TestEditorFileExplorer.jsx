import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"

import { Badge, Box, Button, HorizontalStack, Icon, Navigation, Text, TextField, Tooltip, VerticalStack } from "@shopify/polaris"
import {ChevronDownMinor, ChevronRightMinor, SearchMinor, CirclePlusMinor} from "@shopify/polaris-icons"

import TestEditorStore from "../testEditorStore"

import convertFunc from "../transform"

import "../TestEditor.css"
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import func from "../../../../../util/func"
import { isAgenticSecurityCategory } from "../../../../main/labelHelper"

const TestEditorFileExplorer = ({addCustomTest}) => {

    const testObj = TestEditorStore(state => state.testsObj)
    const selectedTest = TestEditorStore(state => state.selectedTest)
    const setSelectedTest = TestEditorStore(state => state.setSelectedTest)

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

        for (let key in cloneObj.aktoTests) {
            if (cloneObj.aktoTests.hasOwnProperty(key)) {
                let obj = cloneObj.aktoTests[key]
                let arr = obj.filter((test)=>{
                    let name = test.label.toString().toLowerCase().replace(/ /g, "")
                    let category = test.category.toString().toLowerCase().replace(/ /g, "")
                    let content = cloneObj.mapTestToData[test.label].content.toString().toLowerCase();
                    if(name.includes(searchString) || category.includes(searchString) || content.includes(searchString)){
                        aktoTotal++
                        return true
                    }
                })
                cloneObj.aktoTests[key] = arr
            }
        }

        for (let key in cloneObj.customTests) {
            if (cloneObj.customTests.hasOwnProperty(key)) {
                let obj = cloneObj.customTests[key]
                let arr = obj.filter((test)=>{
                    let name = test.label.toString().toLowerCase().replace(/ /g, "")
                    let category = test.category.toString().toLowerCase().replace(/ /g, "")
                    let content = cloneObj.mapTestToData[test.label].content.toString().toLowerCase();
                    if(name.includes(searchString) || category.includes(searchString) || content.includes(searchString)){
                        customTotal++
                        return true
                    }
                })
                cloneObj.customTests[key] = arr
            }
        }

        cloneObj.totalCustomTests = customTotal
        cloneObj.totalAktoTests = aktoTotal
        return cloneObj
    }

    const searchResult = (val) => {
        let cloneObj = JSON.parse(JSON.stringify(testObj))
        setSearchText(val)
        let searchObj = searchFunction(cloneObj, val.toLowerCase())

        setCustomItems(convertFunc.getNavigationItems(searchObj,"CUSTOM",selectedFunc))
        setAktoItems(convertFunc.getNavigationItems(searchObj,"Akto",selectedFunc))
    }

    useEffect(()=>{
        setCustomItems(convertFunc.getNavigationItems(testObj,"CUSTOM",selectedFunc))
        setAktoItems(convertFunc.getNavigationItems(testObj,"Akto",selectedFunc))
    },[testObj])

    useEffect(() => {
        if (selectedTest) {
            const testData = testObj.mapTestToData[selectedTest.label]
            if(testData.type === 'CUSTOM'){
                setSelectedCategory(testData.superCategory + '_custom')
            }else{
                setSelectedCategory(testData.superCategory + '_akto')
            }
        }
     
    }, [selectedTest])

    useEffect(()=> {
        const testData = testObj.mapTestToData[selectedTest.label]
        toggleFunc(testData.type)
    },[])

    function getItems(aktoItems){
        let arr = aktoItems.map(obj => ({
            ...obj,
            selected: selectedCategory === (obj.key+obj.param),
            icon: selectedCategory === (obj.key+obj.param) ? ChevronDownMinor : ChevronRightMinor,
            subNavigationItems: obj.subNavigationItems.map((item)=>{
                return{
                    label: (
                        <Tooltip content={item.label} dismissOnMouseOut width="wide" preferredPosition="below">
                            <div className={item.label === selectedTest.label ? "active-left-test" : ""}>
                                <Text 
                                    variant={item.label === selectedTest.label ? "headingSm" : "bodyMd"} as="h4" 
                                    color={item.label === selectedTest.label ? "default" : "subdued"} truncate
                                >
                                    {item.label} 
                                </Text>
                            </div>
                        </Tooltip>
                    ),
                    onClick: (()=> {
                        navigate(`/dashboard/test-editor/${item.value}`)
                        setSelectedTest(item)                        
                    }),
                    key: item.value
                }
            })
        }))
        if(isAgenticSecurityCategory()){
            arr = func.sortByCategoryPriority(arr, 'key')
        }
        
        return arr;
    }
    return (
        <div className="editor-navbar" style={{'overflowY' : 'scroll', overflowX: 'hidden', width: '18rem'}}>
            <Navigation location="/">
                <VerticalStack gap="4">
                    <TextField  
                        id={"test-search"}
                        prefix={<Icon source={SearchMinor} />} 
                        onChange={searchResult} 
                        value={searchText}
                        placeholder={`Search for Tests`}
                    />

                    <Box>
                        <Button id={"create-custom-test-button"}
                            plain monochrome onClick={()=> toggleFunc("CUSTOM")} removeUnderline fullWidth
                        >
                            <HorizontalStack align="space-between">
                                <HorizontalStack gap={"1"}>
                                    <Box>
                                        <Icon source={showCustom ? ChevronDownMinor : ChevronRightMinor}/>
                                    </Box>
                                    <TitleWithInfo 
                                        tooltipContent={"Custom tests"} 
                                        titleText={"Custom"} 
                                        textProps={{variant: 'headingMd'}} 
                                        docsUrl={"https://docs.akto.io/test-editor/concepts/custom-test"}
                                    />
                                </HorizontalStack>
                                <div style={{marginRight: '-2px'}}>
                                    <Badge size="small" status="new">{customItems.count.toString()}</Badge>
                                </div>
                                {/* <Box onClick={(e) => addCustomTest(e)}>
                                    <Icon source={CirclePlusMinor} />
                                </Box> */}
                            </HorizontalStack>
                        </Button>
                        {showCustom ? <Navigation.Section items={getItems(customItems.items)} /> : null}
                    </Box>
                    <Box>
                        <Button plain monochrome onClick={() => toggleFunc("Akto")} removeUnderline fullWidth>
                            <HorizontalStack align="space-between">
                                <HorizontalStack gap="1">
                                    <Box>
                                        <Icon source={showAkto ? ChevronDownMinor : ChevronRightMinor}/>
                                    </Box>
                                    <TitleWithInfo 
                                        tooltipContent={"Akto's test library"} 
                                        titleText={"Akto default"} 
                                        textProps={{variant: 'headingMd'}} 
                                        docsUrl={"https://docs.akto.io/test-editor/concepts/test-library"}
                                    />
                                </HorizontalStack>
                                <div style={{marginRight: '-2px'}}>
                                    <Badge size="small" status="new">{aktoItems.count.toString()}</Badge>
                                </div>
                            </HorizontalStack>
                        </Button>
                        {showAkto ? <Navigation.Section items={getItems(aktoItems.items)} /> : null}
                    </Box>
                </VerticalStack>
            </Navigation>
        </div>
    )
}

export default TestEditorFileExplorer