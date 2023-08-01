import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"

import { Box, Button, Icon, Navigation, TextField, Tooltip } from "@shopify/polaris"
import {ChevronDownMinor, ChevronRightMinor, SearchMinor, CirclePlusMinor} from "@shopify/polaris-icons"

import TestEditorStore from "../testEditorStore"

import convertFunc from "../transform"

import "../TestEditor.css"

const TestEditorFileExplorer = () => {

    const testObj = TestEditorStore(state => state.testsObj)
    const selectedTest = TestEditorStore(state => state.selectedTest)
    const setSelectedTest = TestEditorStore(state => state.setSelectedTest)

    const [selectedCategory, setSelectedCategory] = useState('none')
    const [customItems, setCustomItems] = useState([])
    const [aktoItems, setAktoItems] = useState([])
    const [searchText, setSearchText] = useState('')
    const [showCustom, setShowCustom] = useState(false)
    const [showAkto, setShowAkto] = useState(false)

    const navigate = useNavigate()

    const selectedFunc = (val) =>{
        setSelectedCategory(val)
    }

    const toggleFunc = (param) =>{
        if(param === 'CUSTOM'){
            setShowCustom(true)
            setShowAkto(false)
        }else{
            setShowAkto(true)
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
        let searchObj = searchFunction(cloneObj, val)

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
            toggleFunc(testData.type)
            if(testData.type === 'CUSTOM'){
                setSelectedCategory(testData.superCategory + '_custom')
            }else{
                setSelectedCategory(testData.superCategory + '_akto')
            }
        }
     
    }, [selectedTest])

    function getItems(aktoItems){
        const arr = aktoItems.map(obj => ({
            ...obj,
            selected: selectedCategory === (obj.key+obj.param),
            icon: selectedCategory === (obj.key+obj.param) ? ChevronDownMinor : ChevronRightMinor,
            subNavigationItems: obj.subNavigationItems.map((item)=>{
                return{
                    label: (
                        <Tooltip content={item.label} dismissOnMouseOut width="wide" preferredPosition="below">
                            <span className={"text-overflow " + (item.label === selectedTest.label ? "active" : "")} style={{'fontSize': '14px'}}>
                                {item.label} 
                            </span>
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
        return arr
    }

    return (
        <div className="editor-navbar" style={{'overflowY' : 'scroll'}}>
            <Navigation location="/">
                <TextField  
                    prefix={<Icon source={SearchMinor} />} 
                    onChange={searchResult} 
                    value={searchText}
                    placeholder={`Search for Tests`}
                />

                <Box>
                    <Button plain monochrome onClick={()=> toggleFunc("CUSTOM")}>
                        <span className="test-header">
                            <Icon source={showCustom ? ChevronDownMinor : ChevronRightMinor} className="left-icon" />
                            <span className="text">
                                Custom
                            </span>
                            <Box onClick={()=> console.log("Icon Clicked")}>
                                <Icon source={CirclePlusMinor} />
                            </Box>
                        </span>
                    </Button>
                    {showCustom ? <Navigation.Section items={getItems(customItems)} /> : null}
                </Box>
                <Box>
                    <Button plain monochrome onClick={() => toggleFunc("Akto")}>
                        <span className="test-header">
                            <Icon source={showAkto ? ChevronDownMinor : ChevronRightMinor} className="left-icon" />
                            <span className="text">
                                Akto Default
                            </span>
                        </span>
                    </Button>
                    {showAkto ? <Navigation.Section items={getItems(aktoItems)} /> : null}
                </Box>
            </Navigation>
        </div>
    )
}

export default TestEditorFileExplorer