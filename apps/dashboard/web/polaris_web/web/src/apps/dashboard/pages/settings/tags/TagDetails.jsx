import { LegacyCard, HorizontalGrid, TextField } from "@shopify/polaris";
import { useLocation, useNavigate } from "react-router-dom";
import { useState, useEffect, useReducer } from "react";
import ConditionsPicker from "../../../components/ConditionsPicker";
import Dropdown from "../../../components/layouts/Dropdown";
import transform from "./transform";
import func from "@/util/func";
import tagsApi from "./api";
import DetailsPage from "../../../components/DetailsPage";
import {produce} from "immer"

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


function TagDetails() {

    const location = useLocation();
    const navigate = useNavigate()
    const isNew = location?.state != undefined && Object.keys(location?.state).length > 0 ? false : true
    const pageTitle = isNew ? "Add tag" : "Configure tag"
    const initialState = isNew ? { name: "", active: undefined, keyConditions: [], operator: "OR" } :
        transform.fillInitialState(location.state);
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

    const errorMessage = func.nameValidationFunc(currState.name || "", !isNew)

    const descriptionCard = (
        <LegacyCard title="Details" key="desc">
            <LegacyCard.Section>
                <HorizontalGrid gap="4" columns={2}>
                    <TextField
                        id={"name-field"} 
                        label="Name" value={currState.name}
                        placeholder='New tag name' 
                        {...isNew  ? {onChange: (val) => handleChange({name: val})} : {}}
                        requiredIndicator={true}
                        {...errorMessage.length > 0 ? {error: errorMessage} : {}}
                    />
                    {isNew ? null :
                        <Dropdown menuItems={activeItems} placeHolder={"Tag active status"}
                            selected={(val) => { handleChange({ active: val }) }} 
                            initial={currState.active} label="Active" />}
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
                id={"url"} 
                title="URL conditions"
                param="param_name"
                conditions={currState.keyConditions || []}
                selectOptions={selectOptions}
                operator={currState.operator}
                dispatch={(val) => {handleDispatch(val, "keyConditions")}}
            />
        </LegacyCard>
    )

    let components = [descriptionCard, conditionsCard]

    const saveAction = async () => {
        let name = currState.name;
        let keyConditionFromUsers = transform.preparePredicatesForApi(currState.keyConditions);
        if ((!keyConditionFromUsers || keyConditionFromUsers.length == 0)) {
            func.setToast(true, true, "Invalid url conditions");
        } else if (errorMessage.length > 0){
            func.setToast(true, true, errorMessage);
        } else {
            if (isNew) {
                tagsApi.saveTagConfig(name, currState.operator, keyConditionFromUsers, true, currState.active).then((res) => {
                    func.setToast(true, false, "Tag config added successfully");
                    setChange(false);
                    let item = res.tagConfig;
                    navigate(null, {
                        state: {
                            name: item?.name, active: item?.active,
                            keyConditions: item?.keyConditions
                        },
                            replace:true
                    })
                })
            } else {
                tagsApi.saveTagConfig(name, currState.operator, keyConditionFromUsers, false, currState.active).then((res) => {
                    func.setToast(true, false, "Tag config updated successfully");
                    setChange(false);
                    let item = res.tagConfig;
                    navigate(null, {
                        state: {
                            name: item?.name, active: item?.active,
                            keyConditions: item?.keyConditions
                        },
                            replace:true
                    })
                })
            }
        }
    }

    const compareFunc = () => {
        return !change
    }

    return (
        <DetailsPage
        pageTitle={pageTitle}
        backUrl="/dashboard/settings/tags"
        saveAction={saveAction}
        discardAction={resetFunc}
        isDisabled={compareFunc}
        components={components}
        />
    )

}

export default TagDetails