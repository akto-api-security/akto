import { ResourceList, LegacyCard, ResourceItem, Modal, Text, Button, TextField, HorizontalStack, List, Link, Checkbox, Badge, VerticalStack } from "@shopify/polaris";
import { useEffect, useState, useCallback } from "react";
import settingsApi from "../api";
import api from "./api";
import func from "@/util/func";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";

function applyCounts(libraries, testCountMap) {
    if (testCountMap == null) {
        return libraries
    }
    for (let repositoryUrl in testCountMap) {
        for (let i in libraries) {
            if (libraries[i].repositoryUrl === repositoryUrl) {
                libraries[i].count = testCountMap[repositoryUrl]
            }
        }
    }
    return libraries
}

function TestLibrary() {

    const aktoTestLibrary = {
        repositoryUrl: "https://github.com/akto-api-security/tests-library/archive/refs/heads/master.zip",
        author: "AKTO", timestamp: 0
    }

    const [data, setData] = useState([aktoTestLibrary]);
    const [canOverrideSystemTemplates, setCanOverrideSystemTemplates] = useState(false)
    const [overrideSystemTemplates, setOverrideSystemTemplates] = useState(false)

    async function fetchData() {
        let res1 = await settingsApi.fetchAdminSettings()
        const canOverride = res1.canOverrideSystemTemplates === true
        setCanOverrideSystemTemplates(canOverride)

        let customLibraries = []
        if (res1.accountSettings.testLibraries != null) {
            customLibraries = res1.accountSettings.testLibraries
        }
        let overrideLibraries = []
        if (canOverride && res1.accountSettings.overrideTestLibraries != null) {
            overrideLibraries = res1.accountSettings.overrideTestLibraries.map((item) => {
                return {...item, overrideSystemTemplates: true}
            })
        }

        let res2 = await api.fetchCustomTestsCount();
        customLibraries = applyCounts(customLibraries, res2.testCountMap)
        overrideLibraries = applyCounts(overrideLibraries, res2.testCountMap)
        setData([aktoTestLibrary, ...customLibraries, ...overrideLibraries])

    }

    useEffect(() => {
        if(window.USER_ROLE === 'ADMIN') {
            fetchData();
        }
    }, [])

    async function handleRemoveTestLibrary(repositoryUrl, isOverride) {
        await api.removeTestLibrary(repositoryUrl, isOverride)
        func.setToast(true, false, "Test library removed successfully")
        fetchData();
    }

    const commonMessage = "It may take a few minutes to sync the library, please refresh the page after sometime to see the changes."

    async function handleSyncTestLibrary(repositoryUrl, isOverride) {
        await api.syncCustomLibrary(repositoryUrl, isOverride)
        func.setToast(true, false, "Test library will be synced in the background. " + commonMessage)
        fetchData();
    }

    async function handleSyncAllDefaultTestLibraries() {
        await api.syncAllDefaultTestLibraries()
        func.setToast(true, false, "All default test libraries will be synced in the background. " + commonMessage)
        fetchData();
    }

    const [addTestLibraryModalActive, setAddTestLibraryModalActive] = useState(false)

    function showAddTestLibraryModal() {
        setOverrideSystemTemplates(false)
        setAddTestLibraryModalActive(true)
    }

    async function addTestLibrary() {
        await api.addTestLibrary(repositoryUrl, overrideSystemTemplates)
        func.setToast(true, false, "Test library added successfully. " + commonMessage)
        if(window.USER_ROLE === 'ADMIN') {
        fetchData()
        }
        setAddTestLibraryModalActive(false)
        setRepositoryUrl('')
        setOverrideSystemTemplates(false)
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
                {canOverrideSystemTemplates ? <List.Item>Override libraries replace system tests that share the same ID. They are applied only while this account is active.</List.Item> : null}
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
                    const { repositoryUrl, author, timestamp, count, overrideSystemTemplates: isOverride } = item;

                    const shortcutActions = author !== "AKTO" ?
                        [
                            {
                                content: 'Sync',
                                onAction: () => { handleSyncTestLibrary(repositoryUrl, isOverride) },
                            },
                            {
                                content: 'Remove test library',
                                onAction: () => { handleRemoveTestLibrary(repositoryUrl, isOverride) },
                            }
                        ] : []

                    return (<ResourceItem
                        id={repositoryUrl}
                        shortcutActions={shortcutActions}
                        persistActions
                        onClick={() => { window.open(getStyledForGithubOrDefault(repositoryUrl, "LINK"), "_blank") }}
                    >
                        <HorizontalStack gap="2" blockAlign="center">
                            <Text variant="bodyMd" fontWeight="bold" as="h3">
                                {`${getStyledForGithubOrDefault(repositoryUrl, "NAME")}`}
                            </Text>
                            {isOverride ? <Badge status="info">System override</Badge> : null}
                        </HorizontalStack>
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
                    <VerticalStack gap="3">
                    <div onKeyDown={(e) => func.handleKeyPress(e, addTestLibrary)}>
                        <HorizontalStack gap={2}>
                            <div style={{ flexGrow: 1 }}>
                                <TextField
                                    id={"repo-url"}
                                    label="Repository url"
                                    placeholder="https://github.com/akto-api-security/tests-library/archive/refs/heads/master.zip"
                                    value={repositoryUrl}
                                    helpText = {overrideSystemTemplates
                                        ? "YAML files whose id matches a system test will replace that system test for this account. Other IDs in the zip are ignored."
                                        : "The repository url must be a zip file ( < 10 MiB ) containing test library YAML files. Make sure the zip file is reachable from your akto dashboard."}
                                    onChange={handleRepositoryUrlChange}
                                    autoComplete="off"
                                />
                            </div>
                        </HorizontalStack>
                    </div>
                    {canOverrideSystemTemplates ? (
                        <Checkbox
                            label="Override system templates"
                            checked={overrideSystemTemplates}
                            onChange={setOverrideSystemTemplates}
                            helpText="Use the same test IDs as Akto system tests to replace them."
                        />
                    ) : null}
                    </VerticalStack>
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
            secondaryActions={window.USER_ROLE === 'ADMIN' ? (
                <Button onClick={handleSyncAllDefaultTestLibraries}>Sync all default libraries</Button>
            ) : null}
            isFirstPage={true}
            divider={true}
        />
    )
}

export default TestLibrary;
