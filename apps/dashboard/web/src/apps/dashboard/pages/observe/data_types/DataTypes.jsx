import React, { useState } from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import { Button, Frame, LegacyCard, ContextualSaveBar, HorizontalGrid, TextField, Box } from '@shopify/polaris'
import Dropdown from '../../../components/layouts/Dropdown'
import "./DataTypes.css"
import ConditionsPicker from '../../../components/ConditionsPicker'

function DataTypes() {

  const [status, setStatus] = useState("true")
  const typeName = "Email"
  const [valueConditions, setValueConditions] = useState([])
  const [keyConditions, setKeyConditions] = useState([])
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

  const selectOptions = [
    {
      label: 'Equals to',
      value: 'equals'
    },
    {
      label: 'Matches regex',
      value: 'regex'
    },
    {
      label: 'Starts with',
      value: 'start'
    },
    {
      label: 'Ends with',
      value: 'end'
    },
    {
      label: 'Is number',
      value: 'number'
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
  ]

  const getRequest = (val) => {
    console.log(val)
  }

  const backAction = () =>{
    console.log("backaction")
  }

  const handleValue = (selected) =>{
    setStatus(selected)
  }

  const descriptionCard = (
    <LegacyCard title="Details" key="desc">
      <LegacyCard.Section>
        <HorizontalGrid gap="4" columns={2}>
          <TextField label="Name" helpText="Name the data type" value={typeName}/>
          <Dropdown menuItems={statusItems} selected={handleValue} initial={status} label= "Active" />
        </HorizontalGrid>
      </LegacyCard.Section>
    </LegacyCard>
  )

  const conditionsCard = (
    <LegacyCard title="Details" key="condition">
        <ConditionsPicker title="Key Conditions" param = "param_name" initialItems={keyConditions} items={selectOptions} />
        <ConditionsPicker title="Value Conditions" param = "param_value" initialItems={valueConditions} items={selectOptions} />
    </LegacyCard>
  )

  const requestCard = (
    <LegacyCard title= "Sensitive" key="sensitive">
      <LegacyCard.Section>
        <p>Mark the location where you want the data type to be sensitive</p>
        <br/>
        <HorizontalGrid columns="4">
          <Dropdown menuItems = {requestItems} initial={requestItems[0].value} selected={getRequest}/>
        </HorizontalGrid >
      </LegacyCard.Section>
    </LegacyCard>
  )

  let components = [descriptionCard, conditionsCard, requestCard]

  const pageMarkup = (
    <PageWithMultipleCards title = "Configure Data Types"
        backAction= {backAction}
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