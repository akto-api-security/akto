import React, { useEffect, useState, useReducer } from 'react'
import { Button, LegacyCard, HorizontalGrid, TextField, VerticalStack, Text } from '@shopify/polaris'
import Dropdown from '../../../components/layouts/Dropdown'
import "./DataTypes.css"
import ConditionsPicker from '../../../components/ConditionsPicker'
import { useLocation, useNavigate } from 'react-router-dom'
import transform from './transform'
import func from "@/util/func"
import api from '../api'
import {produce} from "immer"
import DetailsPage from '../../../components/DetailsPage'

const statusItems = [
  {
    id:"True",
    label: "True",
    value: "true",
  },
  {
    id:"False",
    label: "False",
    value: "false",
  }
]

const operatorOptions = [
  {
    id:"OR",
    label: "OR",
    value: "OR"
  },
  {
    id:"AND",
    label: "AND",
    value: "AND"
  }
]

const selectOptions = [
  {
    id:"EQUALS_TO",
    label: 'Equals to',
    value: 'EQUALS_TO'
  },
  {
    id:"REGEX",
    label: 'Matches regex',
    value: 'REGEX'
  },
  {
    id:"STARTS_WITH",
    label: 'Starts with',
    value: 'STARTS_WITH'
  },
  {
    id:"ENDS_WITH",
    label: 'Ends with',
    value: 'ENDS_WITH'
  },
  {
    id:"IS_NUMBER",
    label: 'Is number',
    value: 'IS_NUMBER',
    type: "NUMBER"
  }
]

const requestItems = [
  {
    id:"1",
    label: 'Request',
    value: '1'
  },
  {
    id:"2",
    label: 'Response',
    value: '2'
  },
  {
    id:"3",
    label: 'Both Request and Response',
    value: '3'
  },
  {
    id:"4",
    label: 'Neither Request nor Response',
    value: '4'
  }
]

function conditionStateReducer(draft, action) {
  try {
    switch (action.type) {
      case "update": {
        if (action.key) {
          Object.assign(draft[action.key], action.obj);
        } else {
          Object.assign(draft, action.obj);
        }
        break;
      }
      case "addPredicates": {
        if (action.key) {
          draft[action.key].predicates.push(action.condition);
        }
        break;
      }
      case "updatePredicates": {
        if (action.key) {
          Object.assign(draft[action.key].predicates[action.index], action.obj)
        }
        break;
      }
      case "deletePredicates": {
        if (action.key) {
          draft[action.key].predicates = draft[action.key].predicates.filter((i, index) => index !== action.index);
        }
        break;
      }
      default: break;
    }
  } catch {
    return draft;
  }
}

function DataTypes() {

  const location = useLocation();
  const navigate = useNavigate()
  const isNew = location?.state == undefined || Object.keys(location?.state).length == 0
  const pageTitle = isNew ? "Add data type" : "Configure data type"
  const initialState = isNew ? transform.initialObj : transform.fillInitialState(location.state);

  const [currState, dispatchCurrState] = useReducer(produce((draft, action) => conditionStateReducer(draft, action)), transform.initialObj);
  const [change, setChange] = useState(false)

  const resetFunc =()=>{
    dispatchCurrState({type:"update", obj:initialState})
    setChange(false)
  }

  useEffect(() => {
      resetFunc()
  },[])

  useEffect(() => {
    if (func.deepComparison(currState, initialState)) {
      setChange(false);
    } else {
      setChange(true);
    }
  }, [currState])

  const saveAction = async () => {
    if (currState.dataType === 'Akto') {
      let obj = {
        name: currState.name,
        ...transform.convertToSensitiveData(currState.sensitiveState)
      }
      api.saveAktoDataType(obj).then((response) => {
        func.setToast(true, false, "Data type updated successfully");
        setChange(false);
        navigate(null, {
          state: { name: response.aktoDataType.name, dataObj: response.aktoDataType, type: "Akto" },
          replace: true
        })
      })
    }
    else {
      let payloadObj = transform.convertDataForCustomPayload(currState)
      api.saveCustomDataType(payloadObj).then((response) => {
        if (isNew) {
          func.setToast(true, false, "Data type added successfully");
        } else {
          func.setToast(true, false, "Data type updated successfully");
        }
        setChange(false);
        navigate(null,
          {
            state: { name: response.customDataType.name, dataObj: response.customDataType, type: "Custom" },
            replace: true
          })
      })
    }
  }

  const handleChange = (obj) => {
    dispatchCurrState({type:"update", obj:obj})
}

  const descriptionCard = (
    <LegacyCard title="Details" key="desc">
      <LegacyCard.Section>
        <HorizontalGrid gap="4" columns={2}>
          <TextField id={"name-field"} label="Name" helpText="Name the data type"
            value={currState.name} placeholder='NEW_CUSTOM_DATA_TYPE'
            onChange={(val) => { isNew ? handleChange({ name: val }) : {} }} />
          {currState.dataType === 'Custom' ?
            <Dropdown id={"active-dropdown"} menuItems={statusItems}
              selected={(val) => { handleChange({ active: val }) }}
              initial={currState.active} label="Active" />
            : null}
        </HorizontalGrid>
      </LegacyCard.Section>
    </LegacyCard>
  )

  const handleDispatch = (val, key) => {
    if (val?.key === "condition") {
      dispatchCurrState({ ...val, key: key, type: `${val.type}Predicates` })
    } else {
      dispatchCurrState({ ...val, key: key });
    }
  }

  const conditionsCard = (
    <LegacyCard title={
      <Text variant='headingMd'>
        Data type conditions
      </Text>
    }
      key="condition">
        <ConditionsPicker 
          id={"key"}
          title="Key conditions" 
          param = "param_name" 
          conditions={currState.keyConditions.predicates}
          selectOptions={selectOptions}
          operator={currState.keyConditions.operator}
          dispatch={(val) => { handleDispatch(val, "keyConditions") }}
        />
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <div style={{ flexGrow: 1, borderBottom: '1px solid #ccc' }}></div>
          <Dropdown id={"condition"} menuItems={operatorOptions} initial={currState.operator} 
          selected={(val) => { handleChange({ operator: val }) }} />
          <div style={{ flexGrow: 1, borderBottom: '1px solid #ccc' }}></div>
        </div>
        <ConditionsPicker 
          id={"value"}
          title="Value conditions" 
          param = "param_value" 
          conditions={currState.valueConditions.predicates}
          selectOptions={selectOptions}
          operator={currState.valueConditions.operator}
          dispatch={(val) => { handleDispatch(val, "valueConditions") }}
        />
    </LegacyCard>
  )

  const compareFunc = () => {
    return !change
  }

  const requestCard = (
    <VerticalStack gap="5" key="sensitive">
      <LegacyCard title= "Sensitive" >
        <LegacyCard.Section>
          <p>Mark the location where you want the data type to be sensitive</p>
          <br/>
          <HorizontalGrid columns="4">
            <Dropdown id={"sensitive-position"} menuItems = {requestItems} initial={currState.sensitiveState} 
            selected={(val) => { handleChange({ sensitiveState: val }) }}/>
          </HorizontalGrid >
        </LegacyCard.Section>
      </LegacyCard>
      <div className='footer-save'>
        <Button id={"save-button"} primary onClick={saveAction} {...compareFunc() ? {disabled: true} : {}}> Save </Button>
      </div>
    </VerticalStack>
  )

  let components = (!isNew && currState.dataType === 'Akto') ? [descriptionCard, requestCard] : [descriptionCard, conditionsCard, requestCard]

  return (
    <DetailsPage
    pageTitle={pageTitle}
    backUrl="/dashboard/observe/sensitive"
    saveAction={saveAction}
    discardAction={resetFunc}
    isDisabled={compareFunc}
    components={components}
    />
  )
}

export default DataTypes