import React, { useEffect, useState } from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import { Button, LegacyCard, HorizontalGrid, TextField, VerticalStack } from '@shopify/polaris'
import Dropdown from '../../../components/layouts/Dropdown'
import "./DataTypes.css"
import ConditionsPicker from '../../../components/ConditionsPicker'
import { useLocation, useNavigate } from 'react-router-dom'
import func from './transform'
import api from '../api'
import ContextualLayout from '../../../components/layouts/ContextualLayout'

function DataTypes() {

  const location = useLocation();
  const mapDataCopy =  (location.state && location.state.resp) ? location.state.resp :location.state && location.state.dataObj ? location.state.dataObj : {}
  const mapData = JSON.parse(JSON.stringify(mapDataCopy));
  const typename = mapData.name ? mapData.name : ''
  const dataType = location.state?.type || "Custom"

  const pageTitle = typename.length > 0 ? "Configure Data Types" : "Add Data Type"
  const [status, setStatus] = useState("false")
  const [typeName,setTypeName] = useState(typename)
  const [valueConditions, setValueConditions] = useState({predicates: [], operator: "OR"})
  const [keyConditions, setKeyConditions] = useState({predicates: [], operator: "OR"})
  const [sensitiveState , setSensitiveState] = useState('4')
  const [operator, setOperator] = useState("OR")
  const [currState , setCurrentState] = useState({})
  const [change, setChange] = useState(false)
  const navigate = useNavigate()

  const statusItems = [
    {
      label: "True",
      value: "true",
    },
    {
      label: "False",
      value: "false",
    }
  ]

  const operatorOptions = [
    {
      label: "OR",
      value: "OR"
    },
    {
      label: "AND",
      value: "AND"
    }
  ]

  const selectOptions = [
    {
      label: 'Equals to',
      value: 'EQUALS_TO'
    },
    {
      label: 'Matches regex',
      value: 'REGEX'
    },
    {
      label: 'Starts with',
      value: 'STARTS_WITH'
    },
    {
      label: 'Ends with',
      value: 'ENDS_WITH'
    },
    {
      label: 'Is number',
      value: 'IS_NUMBER'
    }
  ]

  const requestItems = [
    {
      label: 'Request',
      value: '1'
    },
    {
      label: 'Response',
      value: '2'
    },
    {
      label: 'Both Request and Response',
      value: '3'
    },
    {
      label: 'Neither Request nor Response',
      value: '4'
    }
  ]

  const getRequest = (val) => {
    setSensitiveState(val)
    let updatedState = currState
    updatedState.sensitiveState = val
    setCurrentState(updatedState)
    setChange(true)
  }

  const navigateBack = () =>{
    navigate("/dashboard/observe/sensitive")
  }

  const handleValue = (selected) =>{
    setStatus(selected)
    let updatedState = currState
    updatedState.status = selected
    setCurrentState(updatedState)
    setChange(true)
  }

  const handleOperator = (selected) =>{
    setOperator(selected)
    let updatedState = currState
    updatedState.operator = selected
    setCurrentState(updatedState)
    setChange(true)
  }

  const handleChange = (val) =>{
    if(typename.length === 0){
      setTypeName(val)
      let updatedState = currState
      updatedState.typeName = val
      setCurrentState(updatedState)
      setChange(true)
    }
  }

  let initialObj= {
    status: 'false',
    name: typename,
    valueConditions: {predicates: [], operator: "OR"},
    keyConditions: {predicates: [], operator: "OR"},
    sensitiveState: '4',
    operator: "OR",
  }

  if(typename.length > 0){
    let state = func.convertDataToState(mapData.sensitiveAlways, mapData.sensitivePosition)
    initialObj.sensitiveState = state
    if(dataType === 'Custom'){
      initialObj.status = mapData.active.toString()
      initialObj.operator= mapData.operator
    }
    if(mapData.keyConditions){
      initialObj.keyConditions = mapData.keyConditions
    }
    if(mapData.valueConditions){
      initialObj.valueConditions = mapData.valueConditions
    }
  }

  const resetFunc =()=>{
      setSensitiveState(initialObj.sensitiveState)
      setStatus(initialObj.status)
      setOperator(initialObj.operator)
      setKeyConditions(initialObj.keyConditions)
      setValueConditions(initialObj.valueConditions)
      setCurrentState(initialObj)
      setChange(false)
  }

  useEffect(() => {
      resetFunc()
  },[])

  const compareFunc = ()=>{
    return ((!change))
  }

  const saveAction = async() =>{
    if(dataType === 'Akto'){
      let obj = {
        name: typeName,
        ...func.convertToSensitiveData(sensitiveState)
      }
      await api.saveAktoDataType(obj).then((response) => {
        navigate(null, {state: {resp: response.aktoDataType, type: "Akto"}})
        setChange(false)
      })
    }
    else{
      let keyOp = keyConditions.operator
      let valueOp = valueConditions.operator
      let id = mapData?.id
      let payloadObj = func.convertDataForCustomPayload(keyConditions.predicates,keyOp,valueConditions.predicates,valueOp,operator,typeName,sensitiveState,status,id)
      await api.saveCustomDataType(payloadObj).then((response)=> {
        navigate(null, {state: {resp: response.customDataType}})
        setChange(false)
      })
    }
  }

  const descriptionCard = (
    <LegacyCard title="Details" key="desc">
      <LegacyCard.Section>
        <HorizontalGrid gap="4" columns={2}>
          <TextField label="Name" helpText="Name the data type" value={typeName} placeholder='NEW_CUSTOM_DATA_TYPE' onChange={handleChange}/>
          {dataType === 'Custom' ? <Dropdown menuItems={statusItems} selected={handleValue} initial={status} label= "Active" /> : null}
        </HorizontalGrid>
      </LegacyCard.Section>
    </LegacyCard>
  )

  const conditionsCard = (
    <LegacyCard title="Details" key="condition">
        <ConditionsPicker 
          title="Key Conditions" 
          param = "param_name" 
          initialItems={keyConditions.predicates} 
          items={selectOptions} 
          conditionOp={keyConditions.operator}
          fetchChanges={setKeyConditions}
          setChange={setChange}
        />
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <div style={{ flexGrow: 1, borderBottom: '1px solid #ccc' }}></div>
          <Dropdown menuItems={operatorOptions} initial={operator} selected={handleOperator} />
          <div style={{ flexGrow: 1, borderBottom: '1px solid #ccc' }}></div>
        </div>
        <ConditionsPicker 
          title="Value Conditions" 
          param = "param_value" 
          initialItems={valueConditions.predicates} 
          items={selectOptions} 
          conditionOp={valueConditions.operator}
          fetchChanges={setValueConditions}
          setChange={setChange}
        />
    </LegacyCard>
  )

  const requestCard = (
    <VerticalStack gap="5" key="sensitive">
      <LegacyCard title= "Sensitive" >
        <LegacyCard.Section>
          <p>Mark the location where you want the data type to be sensitive</p>
          <br/>
          <HorizontalGrid columns="4">
            <Dropdown menuItems = {requestItems} initial={sensitiveState} selected={getRequest}/>
          </HorizontalGrid >
        </LegacyCard.Section>
      </LegacyCard>
      <div className='footer-save'>
        <Button onClick={saveAction} {...compareFunc() ? {disabled: true} : {}}> Save </Button>
      </div>
    </VerticalStack>
  )

  let components = (location.state && dataType === 'Akto') ? [descriptionCard, requestCard] : [descriptionCard, conditionsCard, requestCard]

  const pageMarkup = (
    <PageWithMultipleCards title = {pageTitle}
        backAction= {{onAction:navigateBack}}
        divider
        components={components}
    />
  )

  return (
    <ContextualLayout
      saveAction={saveAction}
      discardAction={resetFunc}
      isDisabled={compareFunc}
      pageMarkup={pageMarkup}
    />
  )
}

export default DataTypes