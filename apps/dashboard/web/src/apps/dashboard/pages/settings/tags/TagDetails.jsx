import { LegacyCard, HorizontalGrid, TextField } from "@shopify/polaris";
import { useLocation, useNavigate } from "react-router-dom";
import { useState, useEffect } from "react";
import ConditionsPicker from "../../../components/ConditionsPicker";
import Dropdown from "../../../components/layouts/Dropdown";
import transform from "./transform";
import func from "@/util/func";
import tagsApi from "./api";
import DetailsPage from "../../../components/DetailsPage";

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

const activeItems = [
    {
        label: "True",
        value: "True",
    },
    {
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
    const [currState, setCurrentState] = useState({});
    const [change, setChange] = useState(false)

    const resetFunc = () => {
        setCurrentState((prev) => {
            return { ...initialState }
        });
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
        setCurrentState((prev) => {
            return { ...prev, ...obj };
        })
    }

    const descriptionCard = (
        <LegacyCard title="Details" key="desc">
            <LegacyCard.Section>
                <HorizontalGrid gap="4" columns={2}>
                    <TextField
                        label="Name" value={currState.name}
                        placeholder='New tag name' onChange={(val) => { isNew ? handleChange({ name: val }) : {} }}
                    />
                    {isNew ? null :
                        <Dropdown menuItems={activeItems} placeHolder={"Tag active status"}
                            selected={(val) => { handleChange({ active: val }) }} initial={initialState.active} label="Active" />}
                </HorizontalGrid>
            </LegacyCard.Section>
        </LegacyCard>
    )

    const conditionsCard = (
        <LegacyCard title="Details" key="condition">
            <ConditionsPicker
                title="URL conditions"
                param="param_name"
                initialItems={currState.keyConditions || []}
                items={selectOptions}
                conditionOp={currState.operator}
                fetchChanges={(val) => { handleChange({ keyConditions: val.predicates, operator: val.operator }) }}
                setChange={setChange}
            />
        </LegacyCard>
    )

    let components = [descriptionCard, conditionsCard]

    const saveAction = async () => {
        let name = currState.name;
        let isValidOrError = func.validateName(name);
        let keyConditionFromUsers = transform.preparePredicatesForApi(currState.keyConditions);
        if ((!keyConditionFromUsers || keyConditionFromUsers.length == 0)) {
            func.setToast(true, true, "Invalid url conditions");
        } else if (isValidOrError!==true){
            func.setToast(true, true, isValidOrError);
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
        saveAction={saveAction}
        discardAction={resetFunc}
        isDisabled={compareFunc}
        components={components}
        />
    )

}

export default TagDetails