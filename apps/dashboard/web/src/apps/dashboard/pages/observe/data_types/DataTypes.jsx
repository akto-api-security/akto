import React, { useEffect, useState } from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import { Button, Frame, LegacyCard, ContextualSaveBar, HorizontalGrid, TextField, Box } from '@shopify/polaris'
import Dropdown from '../../../components/layouts/Dropdown'
import "./DataTypes.css"
import ConditionsPicker from '../../../components/ConditionsPicker'
import { useLocation, useNavigate } from 'react-router-dom'
import func from './transform'

function DataTypes() {

  const location = useLocation();

  const mapData= location.state.dataObj ? location.state.dataObj : {}
  const typename = location.state.name

  const pageTitle = typename.length > 0 ? "Configure Data Types" : "Add Data Type"
  const [status, setStatus] = useState("false")
  const [typeName,setTypeName] = useState(typename)
  const [valueConditions, setValueConditions] = useState({predicates: [], operator: "OR"})
  const [keyConditions, setKeyConditions] = useState({predicates: [], operator: "OR"})
  const [sensitiveState , setSensitiveState] = useState('4')
  const [operator, setOperator] = useState("OR")

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
  }

  const navigateBack = () =>{
    navigate("/dashboard/observe/sensitive")
  }

  const handleValue = (selected) =>{
    setStatus(selected)
  }

  const handleOperator = (selected) =>{
    setOperator(selected)
  }

  const handleChange = (val) =>{
    if(typename.length === 0){
      setTypeName(val)
    }
  }

  useEffect(() => {
    if(typename.length > 0){
      setSensitiveState(func.convertDataToState(mapData.sensitiveAlways, mapData.sensitivePosition))
      if(mapData.keyConditions){
        setKeyConditions(mapData.keyConditions)
      }
      if(mapData.valueConditions){
        setValueConditions(mapData.valueConditions)
      }

      if(mapData['active']){
        setStatus(mapData['active'].toString())
      }

      if(mapData['operator']){
        setOperator(mapData['operator'])
      }
    }
  },[])

  const descriptionCard = (
    <LegacyCard title="Details" key="desc">
      <LegacyCard.Section>
        <HorizontalGrid gap="4" columns={2}>
          <TextField label="Name" helpText="Name the data type" value={typeName} placeholder='NEW_CUSTOM_DATA_TYPE' onChange={handleChange}/>
          <Dropdown menuItems={statusItems} selected={handleValue} initial={status} label= "Active" />
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
        />
    </LegacyCard>
  )

  const requestCard = (
    <LegacyCard title= "Sensitive" key="sensitive">
      <LegacyCard.Section>
        <p>Mark the location where you want the data type to be sensitive</p>
        <br/>
        <HorizontalGrid columns="4">
          <Dropdown menuItems = {requestItems} initial={sensitiveState} selected={getRequest}/>
        </HorizontalGrid >
      </LegacyCard.Section>
    </LegacyCard>
  )

  let components = [descriptionCard, conditionsCard, requestCard]

  const pageMarkup = (
    <PageWithMultipleCards title = {pageTitle}
        backAction= {{onAction:navigateBack}}
        divider
        components={components}
    />
  )


  const logo = {
    width: 124,
    contextualSaveBarSource:'/public/logo.svg',
    url: '#',
    accessibilityLabel: 'Akto Icon',
  };


  const contextualMarkup = (
    <ContextualSaveBar
          message="Unsaved changes"
          saveAction={{
            onAction: () => console.log('add form submit logic'),
            loading: false,
            disabled: false,
            content: "Save"
          }}
          discardAction={{
            onAction: () => console.log('add clear form logic'),
            content: "Discard"
          }}
      />
  )

  return (
    <div className='control-frame-padding'>
      <Frame logo={logo}>
        {contextualMarkup}
        {pageMarkup}
      </Frame>
    </div>
  )
}

export default DataTypes