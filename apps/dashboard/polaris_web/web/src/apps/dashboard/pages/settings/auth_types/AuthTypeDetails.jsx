import { LegacyCard, HorizontalGrid, TextField } from "@shopify/polaris";
import { useLocation, useNavigate } from "react-router-dom";
import { useState, useEffect, useReducer } from "react";
import authTypesApi from "./api";
import ConditionsPicker from "../../../components/ConditionsPicker";
import Dropdown from "../../../components/layouts/Dropdown";
import transform from "./transform";
import func from "@/util/func";
import DetailsPage from "../../../components/DetailsPage";
import {produce} from "immer"

const selectOptions = [
    {
        id:"EQUALS_TO",
        label: 'equals to',
        value: 'EQUALS_TO',
        operators: [
            {
                label: 'AND',
                value: 'AND',
            }
        ],  
    },
]

const activeItems = [
    {
      id:"True",
      label: "True",
      value: "True",
    },
    {
      id:"False",
      label: "False",
      value: "False",
    }
  ]

function AuthTypeDetails() {

    const location = useLocation();
    const navigate = useNavigate()
    const isNew = location?.state != undefined && Object.keys(location?.state).length > 0 ? false : true
    const pageTitle = isNew ? "Add auth type" : "Configure auth type"
    const initialState = isNew ? { name: "", active:undefined, headerConditions: [], payloadConditions: [] } : 
        transform.fillInitialState(location.state, selectOptions[0]);
    const [currState, dispatchCurrState] = useReducer(produce((draft, action) => func.conditionStateReducer(draft, action)), {});
    const [change, setChange] = useState(false)
    const resetFunc = () => {
        dispatchCurrState({type:"update", obj:initialState})
        setChange(false);
    }
    useEffect(() => {
        resetFunc()
    }, [])

    useEffect(() => {
        if (func.deepComparison(currState, initialState)) {
            setChange(false);
        } else {
            setChange(true);
        }
    }, [currState])

    const handleChange = (obj) => {
        dispatchCurrState({type:"update", obj:obj})
    }

    const descriptionCard = (
        <LegacyCard title="Details" key="desc">
            <LegacyCard.Section>
                <HorizontalGrid gap="4" columns={2}>
                    <TextField
                        id={"name-field"} 
                        label="Name" value={currState.name}
                        placeholder='New auth type name' onChange={(val) => { isNew ? handleChange({ name: val }) : {} }}
                    />
                    {isNew ? null :
                    <Dropdown id={"active-dropdown"} 
                    menuItems={activeItems} placeHolder={"Auth type active status"}
                    selected={(val) => { handleChange({ active: val }) }} 
                    initial={currState.active} label= "Active" /> } 
                </HorizontalGrid>
            </LegacyCard.Section>
        </LegacyCard>
    )

    const handleDispatch = (val, key) => {
        if(val?.key==="condition"){
            dispatchCurrState({...val, key:key})
        } else {
            dispatchCurrState(val);
        }
    }

    const conditionsCard = (
        <LegacyCard title="Details" key="condition">
            <ConditionsPicker 
              id={"header"}
              title="Header keys" 
              param = "key" 
              conditions={currState.headerConditions || []}
              selectOptions={selectOptions}
              operator={"AND"}
              dispatch={(val) => {handleDispatch(val, "headerConditions")}}
            />
            <div style={{ display: 'flex', alignItems: 'center' }}>
              <div style={{ flexGrow: 1, borderBottom: '1px solid #ccc' }}></div>
              <TextField value={"OR"}/>
              <div style={{ flexGrow: 1, borderBottom: '1px solid #ccc' }}></div>
            </div>
            <ConditionsPicker 
              id={"payload"}
              title="Payload keys" 
              param = "key"
              conditions={currState.payloadConditions || []}
              selectOptions={selectOptions}
              operator={"AND"}
              dispatch={(val) => {handleDispatch(val, "payloadConditions")}}
            />
        </LegacyCard>
      )

    let components = [descriptionCard, conditionsCard]

    const saveAction = async () => {
        let headerKeys = transform.convertPredicateToArray(currState.headerConditions);
        let payloadKeys = transform.convertPredicateToArray(currState.payloadConditions);
        let name = currState.name
        let isValidOrError = func.validateName(name);
        if( (!headerKeys || headerKeys.length==0 ) && (!payloadKeys || payloadKeys.length==0 ) ){
            func.setToast(true, true, "Invalid header or payload keys");
        } else if (isValidOrError!==true){
            func.setToast(true, true, isValidOrError);
        } else {
            if(isNew){
                authTypesApi.addCustomAuthType(name, headerKeys, payloadKeys, true).then((res) => {
                    func.setToast(true, false, "Auth type added successfully");
                    setChange(false);
                    let item = res.customAuthType;
                    navigate(null, { state: { name: item?.name, active: item?.active,
                        headerConditions: item?.headerKeys, payloadConditions: item?.payloadKeys },
                        replace:true})
                }).catch((err) => {
                    func.setToast(true, true, "Unable to add auth type");
                });
            } else {
                authTypesApi.updateCustomAuthType(name, headerKeys, payloadKeys, currState.active).then((res) => {
                    func.setToast(true, false, "Auth type updated successfully");
                    setChange(false);
                    let item = res.customAuthType; 
                    navigate(null, { state: { name: item?.name, active: item?.active,
                        headerConditions: item?.headerKeys, payloadConditions: item?.payloadKeys },
                        replace:true})
                    }).catch((err) => {
                    func.setToast(true, true, "Unable to add auth type");
                });
            }
        }
    }

    const compareFunc = () => {
        return !change
    }

    return (
        <DetailsPage
        pageTitle={pageTitle}
        saveAction={saveAction}
        discardAction={resetFunc}
        isDisabled={compareFunc}
        components={components}
        />
    )
}

export default AuthTypeDetails