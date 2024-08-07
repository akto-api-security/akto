
import { Box, TextField, Modal } from "@shopify/polaris"
import { useState } from "react"
import DropdownSearch from "../../../components/shared/DropdownSearch"
import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs"
import api from "../api";
import func from "@/util/func"
import PersistStore from "../../../../main/PersistStore"

const Operation = {
    ADD: "ADD",
    REMOVE: "REMOVE"
}

function ApiGroupModal(props){

    const {showApiGroupModal, toggleApiGroupModal, apis, operation, currentApiGroupName, fetchData } = props;
    const allCollections = PersistStore(state => state.allCollections);
    const setAllCollections = PersistStore(state => state.setAllCollections)

    const [apiGroupName, setApiGroupName] = useState(currentApiGroupName)

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
            const apiCollections = resp?.apiCollections || []
            func.setToast(true, false, <div data-testid="api_added_to_group_message">APIs added to API group successfully</div>)
            setAllCollections(func.reduceCollectionsResponse(apiCollections))
            toggleApiGroupModal()
        })
    }

    function removeAPIs(){
        let ret = getApis();

        api.removeApisFromCustomCollection(ret, apiGroupName).then((resp)=>{
            const apiCollections = resp?.apiCollections || []
            func.setToast(true, false, "APIs removed from API group successfully")
            setAllCollections(func.reduceCollectionsResponse(apiCollections))
            toggleApiGroupModal()
            fetchData()
        })
    }

    const existingTab = {
        id: 'existing',
        content: 'Existing API group',
        component: (
            <Box padding={5} key={"existing"}>
                <DropdownSearch
                    id={"select-api-group"}
                    label="Select API group"
                    placeholder="Select API group"
                    optionsList={Object.keys(allCollections).filter(x => allCollections[x].type === "API_GROUP").map((x) => {
                        return {
                            label: allCollections[x]?.displayName,
                            value: allCollections[x]?.displayName
                        }
                    })
                    }
                    setSelected={setApiGroupName}
                />
            </Box>
        )
    }

    const newTab = {
        id: 'new',
        content: 'New API group',
        component: (
            <Box padding={5} key={"new"}>
                <TextField
                    id="create-api-group"
                    label="Name"
                    helpText="Enter name for new API group"
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

    const plurality = func.addPlurality(getApis().length);

    const title = operation === Operation.ADD ?`Add API${plurality} to API group` : `Remove API${plurality} from API group`

    const buttonTitle = operation === Operation.ADD ? <div data-testid="add_api_button">{`Add API${plurality}`}</div> : `Remove API${plurality}`

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
                    {`Are you sure you want to remove ${getApis().length} API${plurality} from ${apiGroupName} [Only APIs added manually can be removed]?`}
                </Modal.Section>
            }
        </Modal>
    )
}

export {ApiGroupModal, Operation};