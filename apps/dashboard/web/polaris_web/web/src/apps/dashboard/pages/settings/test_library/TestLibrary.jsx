import { ResourceList, Page, LegacyCard, ResourceItem, Modal, Text, Button, TextField, HorizontalStack, List, Link } from "@shopify/polaris";
import { useEffect, useState, useCallback } from "react";
import settingsApi from "../api";
import api from "./api";
import func from "@/util/func";

function TestLibrary() {

    const aktoTestLibrary = {
        repository:
            { name: "akto-api-security/tests-library", branch: "master" },
        author: "AKTO", timestamp: 0
    }

    const [data, setData] = useState([aktoTestLibrary]);

    async function fetchData() {
        let res1 = await settingsApi.fetchAdminSettings()
        let tmp = []
        if (res1.accountSettings.testLibraries != null) {
            tmp = res1.accountSettings.testLibraries
        }

        let res2 = await api.fetchCustomTestsCount();
        console.log(res2)

        if (res2.testCountMap != null) {
            for(let v in res2.testCountMap){
                let repo = { name: v.split(" ")[0], branch: v.split(" ")[1] }
                for(let i in tmp){
                    if(tmp[i].repository.name === repo.name && tmp[i].repository.branch === repo.branch){
                        tmp[i].count = res2.testCountMap[v]
                    }
                }
            }
        }
        console.log(tmp);
        setData([aktoTestLibrary, ...tmp])

    }

    useEffect(() => {
        fetchData();
    }, [])

    async function handleRemoveTestLibrary(repository) {
        await api.removeTestLibrary({
            repository: repository.name,
            branch: repository.branch
        })
        func.setToast(true, false, "Test library removed successfully")
        fetchData();
    }

    async function handleSyncTestLibrary(repository) {
        await api.syncCustomLibrary({
            repository: repository.name,
            branch: repository.branch
        })
        func.setToast(true, false, "Test library will be synced in the background. It may take a few minutes to sync the library.")
        fetchData();
    }

    const [addTestLibraryModalActive, setAddTestLibraryModalActive] = useState(false)

    function showAddTestLibraryModal() {
        setAddTestLibraryModalActive(true)
    }

    async function addTestLibrary() {
        await api.addTestLibrary({ repository: repositoryName, branch: repositoryBranch })
        func.setToast(true, false, "Test library added successfully. It may take a few minutes to sync the library.")
        fetchData()
        setAddTestLibraryModalActive(false)
        setRepositoryName('')
        setRepositoryBranch('')
    }

    const [repositoryName, setRepositoryName] = useState('');
    const handleRepositoryNameChange = useCallback(
        (newValue) => setRepositoryName(newValue),
        []);

    const [repositoryBranch, setRepositoryBranch] = useState('');
    const handleRepositoryBranchChange = useCallback(
        (newValue) => setRepositoryBranch(newValue),
        []);

    function getInfo({author, timestamp, count}){
        if(count == null || count == undefined) 
            return `Added by ${author} ${func.prettifyEpoch(timestamp)}`

        return `${count} test${count == 1 ? "" : "s" } added by ${author} ${func.prettifyEpoch(timestamp)}`
    }

    return (
        <Page
            title="Test library"
            divider
            primaryAction={<Button
                id={"add-test-library"}
                primary
                onClick={showAddTestLibraryModal}
            >
                Add new test library
            </Button>}
        >
            <LegacyCard title={"Manage test libraries"} sectioned>
                <List type="bullet">
                    <List.Item>Use distinct IDs for tests in the test library. If a custom test library contains a test with the same id as an existing test, the test from the custom test library will be ignored.</List.Item>
                    <List.Item>To write custom tests visit our <Link target="_blank" url="https://docs.akto.io/test-editor/writing-custom-tests">docs</Link>.</List.Item>
                </List>
            </LegacyCard>

            <LegacyCard>
                <ResourceList
                    resourceName={{ singular: 'test library', plural: 'test libraries' }}
                    items={data}
                    renderItem={(item) => {
                        const { repository, author, timestamp, count } = item;

                        const shortcutActions = author !== "AKTO" ?
                            [
                                {
                                    content: 'Sync',
                                    onAction: () => { handleSyncTestLibrary(repository) },
                                },
                                {
                                    content: 'Remove test library',
                                    onAction: () => { handleRemoveTestLibrary(repository) },
                                }
                            ] : []

                        return (<ResourceItem
                            id={repository.name + repository.branch}
                            shortcutActions={shortcutActions}
                            persistActions
                            onClick={() => { window.open(`https://github.com/${repository.name}/tree/${repository.branch}`, "_blank") }}
                        >
                            <Text variant="bodyMd" fontWeight="bold" as="h3">
                                {`${repository.name}:${repository.branch}`}
                            </Text>
                            <Text variant="bodyMd">
                                {author !== "AKTO" ?
                                    getInfo({author, timestamp, count})
                                : "Default test library"}
                            </Text>
                        </ResourceItem>
                        );
                    }}
                />
                <Modal
                    key="modal"
                    open={addTestLibraryModalActive}
                    onClose={() => setAddTestLibraryModalActive(false)}
                    title="New test library"
                    primaryAction={{
                        id: "add-new-test-library",
                        content: 'Add',
                        onAction: addTestLibrary,
                    }}
                >
                    <Modal.Section>
                        <div onKeyDown={(e) => func.handleKeyPress(e, addTestLibrary)}>
                            <HorizontalStack gap={2}>
                                <div style={{ flexGrow: 1 }}>
                                    <TextField
                                        id={"repo-name"}
                                        label="Repository"
                                        placeholder="akto-api-security/tests-library"
                                        value={repositoryName}
                                        onChange={handleRepositoryNameChange}
                                        autoComplete="off"
                                    />
                                </div>
                                <div style={{ flexGrow: 1 }}>
                                    <TextField
                                        id={"repo-branch"}
                                        label="Branch"
                                        placeholder="master"
                                        value={repositoryBranch}
                                        onChange={handleRepositoryBranchChange}
                                        autoComplete="off"
                                    />
                                </div>
                            </HorizontalStack>
                        </div>
                    </Modal.Section>
                </Modal>
            </LegacyCard>
        </Page>
    )
}

export default TestLibrary;