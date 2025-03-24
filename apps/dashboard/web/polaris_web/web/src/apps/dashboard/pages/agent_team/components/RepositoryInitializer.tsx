import { Box, Button, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'
import SSOTextfield from '../../../../signup/components/SSOTextfield'
import { RepoPayload, RepoType } from '../types'
import func from "../../../../../util/func"
import DropdownSearch from '../../../components/shared/DropdownSearch'
import agentApi from '../api'

function RepoSelector({ handleClickRepo, selectedRepo, selectedProject, selectedConnection }) {
    const [reposList, setReposList] = useState<RepoPayload[]>([])
    const [newRepoName, setNewRepoName] = React.useState<string>('');
    const [newProjectName, setNewProjectName] = React.useState<string>('');

    const handleAddRepository = async () => {
        if (newRepoName && newProjectName) {
            // Add the new repository to the list
            const newRepo: RepoPayload = {
                repo: newRepoName,
                project: newProjectName,
                lastRun: 0,  // Default value for new repo
                scheduleTime: 0  // Default value for new repo
            };
            setReposList(prev => [...prev, newRepo]);

            // Trigger the analysis for the new repository
            await handleClickRepo(newRepoName, newProjectName, null);

            // Clear the input fields
            setNewRepoName('');
            setNewProjectName('');
        }
    };

    return (
        <Box as='div' paddingBlockStart={"5"}>
            <VerticalStack gap={"4"}>
                {/* New repository input section */}
                <VerticalStack gap={"2"}>
                    <Text variant="bodyMd" as='span'>
                        Add a new repository:
                    </Text>
                    <HorizontalStack gap={"2"} align="start">
                        <Box width='200px'>
                            <TextField
                                label="Repository Name"
                                autoComplete="off"
                                value={newRepoName}
                                onChange={(value) => setNewRepoName(value)}
                                placeholder="Enter repository name"
                            />
                        </Box>
                        <Box width='200px'>
                            <TextField
                                label="Project Name"
                                autoComplete="off"
                                value={newProjectName}
                                onChange={(value) => setNewProjectName(value)}
                                placeholder="Enter project name"
                            />
                        </Box>
                        <Button
                            onClick={handleAddRepository}
                            disabled={!newRepoName || !newProjectName}
                            primary
                        >
                            Add Repository
                        </Button>
                    </HorizontalStack>
                </VerticalStack>

                {/* Recently added section */}
                {/* <VerticalStack gap={"2"}>
                    <Text variant="bodySm" as='span' color='subdued'>Recently added:</Text>
                    <GridRows
                        items={reposList.slice(0, 5).map((x) => {
                            return {
                                ...x,
                                icon: getIcon(selectedConnection)
                            }
                        })}
                        CardComponent={SelectRepoComp}
                        horizontalGap={"3"}
                        verticalGap={"3"}
                        columns={3}
                        onButtonClick={(repo: string, project: string) => {
                            handleClickRepo(repo, project, null)
                        }}
                    />
                </VerticalStack> */}
            </VerticalStack>
        </Box>
    )
}

function RepositoryInitializer({ agentType }: { agentType: string }) {
    const [selectedConnection, setSelectedConnection] = React.useState<string>('')
    const [selectedRepo, setSelectedRepo] = React.useState<string>('')
    const [selectedProject, setSelectedProject] = React.useState<string>('')
    const [temp, setTemp] = React.useState<string>('')
    const getIcon = (id: string) => {
        switch (id) {
            case 'GITHUB':
                return '/public/github.svg';
            case 'GITLAB':
                return '/public/gitlab.svg';
            case 'BIT_BUCKET':
                return '/public/bitbucket.svg';
            default:
                return '';
        }
    }
    const handleClick = async (id: string) => {
        try {
            setSelectedConnection(id)
            // setReposList(formattedRepos.sort((a, b) => b.lastRun - a.lastRun));
            // const resp: any = await api.fetchCodeAnalysisRepos(id);
            // if(resp?.codeAnalysisRepos.length !== 0) {
            //     const formattedRepos: RepoPayload[] = resp?.codeAnalysisRepos.map((x: any) => ({
            //         repo: x.repoName,
            //         project: x.projectName,
            //         lastRun: x.lastRun,
            //         scheduleTime: x.scheduleTime,
            //     }));
            // }
        } catch (error) {
            window.open("/dashboard/quick-start?connect=" + id.toLowerCase(), "_blank");
        }
    }

    const handleClickRepo = async (repo: string, project: string, localString: string | null) => {
        setSelectedProject(project);
        setSelectedRepo(repo);

        const data = localString !== null
            ? { projectDir: localString }
            : selectedConnection && repo && project
                ? {
                    sourceCodeType: selectedConnection,
                    repository: repo,
                    project: project
                }
                : {};

        if (Object.keys(data).length === 0) return;

        await agentApi.createAgentRun({ agent: agentType, data });
        func.setToast(true, false, "Starting agent");
    };

    const connectionOptions: RepoType[] = [
        {
            id: 'GITHUB',
            logo: '/public/github.svg',
            text: 'Continue with GitHub',
            onClickFunc: () => handleClick('GITHUB')
        },
        {
            id: 'GITLAB',
            logo: '/public/gitlab.svg',
            text: 'Continue with GitLab',
            onClickFunc: () => handleClick('GITLAB')
        },
        {
            id: 'BIT_BUCKET',
            logo: '/public/bitbucket.svg',
            text: 'Continue with BitBucket',
            onClickFunc: () => handleClick('BIT_BUCKET')
        }
    ]

    function RepoInitializer() {
        return (
            <Box as='div' paddingBlockStart={"5"}>
                <HorizontalStack align="center">
                    <Box width='400px'>
                        <VerticalStack gap={"4"} align="center">
                            <VerticalStack gap={"2"} align='center'>
                                <Text alignment="center" as="span" variant="headingMd">Integrate the tools</Text>
                                <Text alignment="center" as="span" variant="bodySm">Connect your repository to begin scanning and protecting your code. Choose your preferred source.</Text>
                            </VerticalStack>
                            <VerticalStack gap={"2"} align='center'>
                                {connectionOptions.map((connection, index) => (
                                    <SSOTextfield key={index} logos={[connection.logo]} text={connection.text} onClickFunc={connection.onClickFunc} />
                                ))}
                                {func.checkLocal() ? <TextField
                                    label="Repository URL"
                                    autoComplete="off"
                                    placeholder="Enter your repository URL"
                                    value={temp}
                                    focused={true}
                                    onChange={(x: string) => setTemp(x)}
                                    connectedRight={<Button onClick={() => handleClickRepo("", "", temp)}>Start</Button>}
                                /> : null}
                            </VerticalStack>
                        </VerticalStack>
                    </Box>
                </HorizontalStack>
            </Box>
        )
    }

    return (
        selectedConnection.length === 0 ? <RepoInitializer /> : <RepoSelector 
            handleClickRepo={handleClickRepo}
            selectedRepo={selectedRepo}
            selectedProject={selectedProject}
            selectedConnection={selectedConnection}
        />
    )
}

export default RepositoryInitializer