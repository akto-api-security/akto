
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

    const {showApiGroupModal, toggleApiGroupModal, apis, operation } = props;

    const setCollectionsMap = PersistStore(state => state.setCollectionsMap)
    const allCollections = PersistStore(state => state.allCollections);
    const setAllCollections = PersistStore(state => state.setAllCollections)

    const [apiGroupName, setApiGroupName] = useState("")

    function getApis(){
        return apis.map((x) => {
            let tmp = x.split(" ");
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
            func.setToast(true, false, "APIs added to API group successfully")
            setCollectionsMap(func.mapCollectionIdToName(resp?.apiCollections))
            setAllCollections(resp?.apiCollections)
            setTimeout(() => {
                toggleApiGroupModal()
            }, 500)
        })
    }

    function removeAPIs(){
        let ret = getApis();

        api.removeApisFromCustomCollection(ret, apiGroupName).then((resp)=>{
            func.setToast(true, false, "APIs removed from API group successfully")
            setCollectionsMap(func.mapCollectionIdToName(resp?.apiCollections))
            setAllCollections(resp?.apiCollections)
            setTimeout(() => {
                toggleApiGroupModal()
            }, 500)
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
                    optionsList={allCollections.filter((x) => { return x.type === 'API_GROUP' }).map((x) => {
                        return {
                            label: x.displayName,
                            value: x.displayName
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

    const title = operation === Operation.ADD ? "Add APIs to API group" : "Remove APIs from API group"

    const buttonTitle = operation === Operation.ADD ? "Add APIs" : "Remove APIs"

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
        >
            <Modal.Section flush>
                <LayoutWithTabs
                    key="tabs"
                    tabs={tabs}
                    currTab={() => {}}
                    noLoading={true}
                />

            </Modal.Section>
        </Modal>
    )
}

export {ApiGroupModal, Operation};