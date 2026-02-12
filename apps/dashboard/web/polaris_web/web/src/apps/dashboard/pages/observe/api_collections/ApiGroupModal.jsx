
import { Box, TextField, Modal } from "@shopify/polaris"
import { useState } from "react"
import DropdownSearch from "../../../components/shared/DropdownSearch"
import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs"
import api from "../api";
import func from "@/util/func"
import PersistStore from "../../../../main/PersistStore"
import { mapLabel, getDashboardCategory } from "../../../../main/labelHelper"

const Operation = {
    ADD: "ADD",
    REMOVE: "REMOVE"
}

function ApiGroupModal(props){

    const {showApiGroupModal, toggleApiGroupModal, apis, operation, currentApiGroupName, fetchData } = props;

    const setCollectionsMap = PersistStore(state => state.setCollectionsMap)
    const allCollections = PersistStore(state => state.allCollections);
    const setAllCollections = PersistStore(state => state.setAllCollections)
    const activatedGroupCollections = allCollections.filter((x) => { return (x.type === 'API_GROUP' && x.deactivated === false) })

    const [apiGroupName, setApiGroupName] = useState(currentApiGroupName)

    const entityLabel = mapLabel('API', getDashboardCategory())
    const groupLabel = mapLabel('API group', getDashboardCategory())

    function getApis(){
        return apis.map((x) => {
            let tmp = x.split("###");
            return {
                method: tmp[0],
                url: tmp[1],
                apiCollectionId: parseInt(tmp[2])
            }
        })
    }

    function addAPIs(){
        let ret = getApis();

        api.addApisToCustomCollection(ret, apiGroupName).then((resp)=>{
            func.setToast(true, false, <div data-testid="api_added_to_group_message">{`${entityPlural} added to ${groupLabel} successfully`}</div>)
            setCollectionsMap(func.mapCollectionIdToName(resp?.apiCollections))
            setAllCollections(resp?.apiCollections)
            toggleApiGroupModal()
        })
    }

    function removeAPIs(){
        let ret = getApis();

        api.removeApisFromCustomCollection(ret, apiGroupName).then((resp)=>{
            func.setToast(true, false, `${entityPlural} removed from ${groupLabel} successfully`)
            setCollectionsMap(func.mapCollectionIdToName(resp?.apiCollections))
            setAllCollections(resp?.apiCollections)
            toggleApiGroupModal()
            fetchData()
        })
    }

    const existingTab = {
        id: 'existing',
        content: 'Existing Group',
        component: (
            <Box padding={5} key={"existing"}>
                <DropdownSearch
                    id={"select-api-group"}
                    label={`Select ${groupLabel}`}
                    placeholder={`Select ${groupLabel}`}
                    optionsList={
                        activatedGroupCollections.map((x) => {
                            return {
                                label: x.displayName,
                                value: x.displayName
                            }
                        })
                    }
                    value={apiGroupName}
                    setSelected={setApiGroupName}
                />
            </Box>
        )
    }

    const newTab = {
        id: 'new',
        content: 'New Group',
        component: (
            <Box padding={5} key={"new"}>
                <TextField
                    id="create-api-group"
                    label="Name"
                    helpText={`Enter name for new ${groupLabel}`}
                    value={apiGroupName}
                    onChange={(input) => setApiGroupName(input)}
                    autoComplete="off"
                    maxLength="25"
                />
            </Box>
        )
    }

    let tabs = [existingTab]
    if(operation === Operation.ADD){
        tabs.push(newTab)
    }

    const apisCount = apis.length
    const plurality = func.addPlurality(apisCount);
    const entityPlural = plurality === 's' ? `${entityLabel}s` : entityLabel

    const title = operation === Operation.ADD
        ? `Add to ${groupLabel}`
        : `Remove from ${groupLabel}`

    const buttonTitle = operation === Operation.ADD
        ? <div data-testid="add_api_button">{`Add ${entityPlural}`}</div>
        : `Remove ${entityPlural}`

    const buttonAction = operation === Operation.ADD ? addAPIs : removeAPIs

    return (
        <Modal
            key={"api-group-modal"}
            open={showApiGroupModal}
            onClose={toggleApiGroupModal}
            title={title}
            primaryAction={{
                content: buttonTitle,
                onAction: buttonAction,
            }}
            secondaryActions={[
                {
                    content: 'Cancel',
                    onAction: toggleApiGroupModal,
                },
            ]}
        >
            { operation === Operation.ADD ?
                <Modal.Section flush>
                    <LayoutWithTabs
                        key="tabs"
                        tabs={tabs}
                        currTab={() => {}}
                        noLoading={true}
                    />
                </Modal.Section> :
                <Modal.Section>
                    {`Are you sure you want to remove ${apisCount} ${entityPlural} from ${apiGroupName} [Only ${entityLabel}s added manually can be removed]?`}
                </Modal.Section>
            }
        </Modal>
    )
}

export {ApiGroupModal, Operation};