import { Box, Button, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'
import SSOTextfield from '../../../../signup/components/SSOTextfield'
import { RepoPayload, RepoType } from '../types'
import api from '../../quick_start/api' 
import { useAgentsStore } from '../agents.store'
import func from "../../../../../util/func"
import GridRows from '../../../components/shared/GridRows'
import SelectRepoComp from './agentResponses/SelectRepoComp'
import DropdownSearch from '../../../components/shared/DropdownSearch'
import agentApi from '../api'

function RepositoryInitializer({agentType}: {agentType: string}) {
    const { setSelectedRepository } = useAgentsStore()
    const [reposList, setReposList] = useState<RepoPayload[]>([])
    const [selectedConnection, setSelectedConnection] = React.useState<string>('')
    const [selectedRepo, setSelectedRepo] = React.useState<string>('')
    const [selectedProject, setSelectedProject] = React.useState<string>('')
    const [temp, setTemp] = React.useState<string>('')
    const getIcon = (id:string) => {
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
            const resp: any = await api.fetchCodeAnalysisRepos(id);
            if(resp?.codeAnalysisRepos.length !== 0) {
                const formattedRepos: RepoPayload[] = resp?.codeAnalysisRepos.map((x: any) => ({
                    repo: x.repoName,
                    project: x.projectName,
                    lastRun: x.lastRun,
                    scheduleTime: x.scheduleTime,
                }));
                setReposList(formattedRepos.sort((a, b) => b.lastRun - a.lastRun));
                setSelectedConnection(id)
            }else{
                window.open("/dashboard/quick-start?connect=" + id.toLowerCase(), "_blank");
            }
        } catch (error) {
            window.open("/dashboard/quick-start?connect=" + id.toLowerCase(), "_blank");
        }
    }

    const handleClickRepo = async (repo: string, project: string, localString: string | null) => {
        setSelectedProject(project);
        setSelectedRepo(repo);  
        setSelectedRepository(repo + "/" + project);
        await agentApi.createAgentRun({
            agent: agentType,
            data: {
                projectDir: func.checkLocal() ? localString : repo + "/" + project
            }
        })
    }   
    
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

    function RepoInitializer () {
        return(
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
                                    onChange={(x:string) => setTemp(x)} 
                                    connectedRight={<Button onClick={() => handleClickRepo("", "", temp)}>Start</Button>} 
                                /> : null}
                            </VerticalStack>
                        </VerticalStack>
                    </Box>
                </HorizontalStack>
            </Box>
        )
    }

    function RepoSelector () {
        return(
            <Box as='div' paddingBlockStart={"5"}>
                <VerticalStack gap={"4"}>
                    <Text as='span' variant='bodyMd'>
                        Hey! I see you've connected your {func.toSentenceCase(selectedConnection)} account. Found {reposList.length} repositories which one would you like me to analyze?
                    </Text>
                    <Box width='350px' paddingInlineStart={"2"}>
                        <DropdownSearch
                            placeholder="Select a repository"
                            optionsList={
                                reposList.map((x) => {
                                    return{
                                        label: x.project + " / " + x.repo ,
                                        value: x.repo + "/" + x.project,
                                    }
                                })
                            }
                            setSelected={(x:string) => {
                                handleClickRepo(x.split("/")[0], x.split("/")[1], null)
                            }}
                            value={selectedRepo.length > 0 ?selectedRepo + "/" + selectedProject : ""}
                        />
                    </Box>
                    
                    <VerticalStack gap={"2"}>
                        <Text variant="bodySm" as='span' color='subdued'>Recently added:</Text>
                        <GridRows items={reposList.slice(0,5).map((x) => {
                            return {
                                    ...x,
                                    icon: getIcon(selectedConnection)
                                }
                            })} CardComponent={SelectRepoComp} horizontalGap={"3"} verticalGap={"3"} columns={3}
                            onButtonClick={(repo:string, project:string) => {
                                handleClickRepo(repo, project, null)
                            }}
                        />
                    </VerticalStack>
                </VerticalStack>
            </Box>
        )
    }

    return (
       selectedConnection.length === 0 ? <RepoInitializer/> : <RepoSelector/>
    )
}

export default RepositoryInitializer