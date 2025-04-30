import { ResourceList, LegacyCard, ResourceItem, Modal, Text, Button, TextField, HorizontalStack, List, Link } from "@shopify/polaris";
import { useEffect, useState, useCallback } from "react";
import settingsApi from "../api";
import api from "./api";
import func from "@/util/func";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";

function TestLibrary() {

    const aktoTestLibrary = {
        repositoryUrl: "https://github.com/akto-api-security/tests-library/archive/refs/heads/master.zip",
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

        if (res2.testCountMap != null) {
            for(let repositoryUrl in res2.testCountMap){
                for(let i in tmp){
                    if(tmp[i].repositoryUrl === repositoryUrl) {
                        tmp[i].count = res2.testCountMap[repositoryUrl]
                    }
                }
            }
        }
        setData([aktoTestLibrary, ...tmp])

    }

    useEffect(() => {
        if(window.USER_ROLE === 'ADMIN') {
            fetchData();
        }
    }, [])

    async function handleRemoveTestLibrary(repositoryUrl) {
        await api.removeTestLibrary(repositoryUrl)
        func.setToast(true, false, "Test library removed successfully")
        fetchData();
    }

    const commonMessage = "It may take a few minutes to sync the library, please refresh the page after sometime to see the changes."

    async function handleSyncTestLibrary(repositoryUrl) {
        await api.syncCustomLibrary(repositoryUrl)
        func.setToast(true, false, "Test library will be synced in the background. " + commonMessage)
        fetchData();
    }

    const [addTestLibraryModalActive, setAddTestLibraryModalActive] = useState(false)

    function showAddTestLibraryModal() {
        setAddTestLibraryModalActive(true)
    }

    async function addTestLibrary() {
        await api.addTestLibrary(repositoryUrl)
        func.setToast(true, false, "Test library added successfully. " + commonMessage)
        fetchData()
        setAddTestLibraryModalActive(false)
        setRepositoryUrl('')
    }

    const [repositoryUrl, setRepositoryUrl] = useState('');
    const handleRepositoryUrlChange = useCallback(
        (newValue) => setRepositoryUrl(newValue),
        []);

    function getInfo({author, timestamp, count}){
        if(count == null || count == undefined) 
            return `Added by ${author} ${func.prettifyEpoch(timestamp)}`

        return `${count} test${func.addPlurality(count)} added by ${author} ${func.prettifyEpoch(timestamp)}`
    }

    function getStyledForGithubOrDefault(repositoryUrl, type){

        const regex = /https:\/\/github\.com\/([^\/]+\/[^\/]+)\/archive\/refs\/heads\/(.+)\.zip/;
        const match = repositoryUrl.match(regex);

        if (match && match.length === 3) {
            const repositoryURL = match[1];
            const branchName = match[2];

            if (type == "LINK")
                return `https://github.com/${repositoryURL}/tree/${branchName}`;
            else if (type == "NAME")
                return `${repositoryURL}:${branchName}`
        }
        return repositoryUrl;
    }

    const titleComp= (
        <LegacyCard title={"Manage test libraries"} sectioned key={"titleComp"}>
            <List type="bullet">
                <List.Item>Use distinct IDs for tests in the test library. If a custom test library contains a test with the same id as an existing test, the test from the custom test library will be ignored.</List.Item>
                <List.Item>To write custom tests visit our <Link target="_blank" url="https://docs.akto.io/test-editor/writing-custom-tests">docs</Link>.</List.Item>
            </List>
        </LegacyCard>
    )

    const bodyComp = (
        <LegacyCard key={"bodyComp"}>
            <ResourceList
                resourceName={{ singular: 'test library', plural: 'test libraries' }}
                items={data}
                renderItem={(item) => {
                    const { repositoryUrl, author, timestamp, count } = item;

                    const shortcutActions = author !== "AKTO" ?
                        [
                            {
                                content: 'Sync',
                                onAction: () => { handleSyncTestLibrary(repositoryUrl) },
                            },
                            {
                                content: 'Remove test library',
                                onAction: () => { handleRemoveTestLibrary(repositoryUrl) },
                            }
                        ] : []

                    return (<ResourceItem
                        id={repositoryUrl}
                        shortcutActions={shortcutActions}
                        persistActions
                        onClick={() => { window.open(getStyledForGithubOrDefault(repositoryUrl, "LINK"), "_blank") }}
                    >
                        <Text variant="bodyMd" fontWeight="bold" as="h3">
                            {`${getStyledForGithubOrDefault(repositoryUrl, "NAME")}`}
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
                                    id={"repo-url"}
                                    label="Repository url"
                                    placeholder="https://github.com/akto-api-security/tests-library/archive/refs/heads/master.zip"
                                    value={repositoryUrl}
                                    helpText = "The repository url must be a zip file ( < 10 MiB ) containing test library YAML files. Make sure the zip file is reachable from your akto dashboard."
                                    onChange={handleRepositoryUrlChange}
                                    autoComplete="off"
                                />
                            </div>
                        </HorizontalStack>
                    </div>
                </Modal.Section>
            </Modal>
        </LegacyCard>
    )

    const components = [titleComp, bodyComp]

    return (

        <PageWithMultipleCards
            components={components}
            title={
                <Text variant='headingLg' truncate>
                    Test library
                </Text>
            }
            primaryAction={<Button primary onClick={showAddTestLibraryModal}>Add new test library</Button>}
            isFirstPage={true}
            divider={true}
        />
    )
}

export default TestLibrary;