import { Box, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'
import SSOTextfield from '../../../../signup/components/SSOTextfield'
import { RepoPayload, RepoType } from '../types'
import api from '../../quick_start/api' 
import { useAgentsStore } from '../agents.store'
import func from "../../../../../util/func"
import GridRows from '../../../components/shared/GridRows'
import SelectRepoComp from './agentResponses/SelectRepoComp'
import DropdownSearch from '../../../components/shared/DropdownSearch'

function RepositoryInitializer() {
    const { setSelectedRepository } = useAgentsStore()
    const [reposList, setReposList] = useState<RepoPayload[]>([])
    const [selectedConnection, setSelectedConnection] = React.useState<string>('')
    const [selectedRepo, setSelectedRepo] = React.useState<string>('')
    const [selectedProject, setSelectedProject] = React.useState<string>('')
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
            const temp: RepoPayload[] = [
                {
                    "repo": "Spring-Boot-Rest-API",
                    "project": "Ankita28g",
                    "lastRun": 1727775651,
                    "scheduleTime": 1727775596
                },
                {
                    "repo": "evilayet",
                    "project": "shivam-rawat-akto",
                    "lastRun": 1731478945,
                    "scheduleTime": 1731478939
                },
                {
                    "repo": "spring-boot-rest-example",
                    "project": "khoubyari",
                    "lastRun": 1727779752,
                    "scheduleTime": 1727779745
                },
                {
                    "repo": "gs-rest-service",
                    "project": "spring-guides",
                    "lastRun": 1727786458,
                    "scheduleTime": 1727786454
                },
                {
                    "repo": "Spring-Boot-Rest-API",
                    "project": "ankush-jain-akto",
                    "lastRun": 1727786841,
                    "scheduleTime": 1727786786
                },
                {
                    "repo": "springboot-rest-example",
                    "project": "cyberbliss",
                    "lastRun": 1727787835,
                    "scheduleTime": 1727787790
                },
                {
                    "repo": "spring-boot-mysql-rest-api-tutorial",
                    "project": "callicoder",
                    "lastRun": 1727788059,
                    "scheduleTime": 1727788014
                },
                {
                    "repo": "todo-api",
                    "project": "mohitkumarsahni",
                    "lastRun": 1727788231,
                    "scheduleTime": 1727788224
                },
                {
                    "repo": "Salary-Maker-Backend",
                    "project": "moniruzzamanrony",
                    "lastRun": 1727788336,
                    "scheduleTime": 1727788283
                },
                {
                    "repo": "blog-application-spring-boot-rest-api-04",
                    "project": "Ankita28g",
                    "lastRun": 1727803947,
                    "scheduleTime": 1727803885
                },
                {
                    "repo": "Shop-API",
                    "project": "AnhJun18",
                    "lastRun": 1731988374,
                    "scheduleTime": 1731987945
                },
                {
                    "repo": "multi_proto",
                    "project": "avneesh99",
                    "lastRun": 1731988376,
                    "scheduleTime": 1731987658
                },
                {
                    "repo": "truck_signs_api",
                    "project": "shivam-rawat-akto",
                    "lastRun": 1738941533,
                    "scheduleTime": 1738941279
                },
                {
                    "repo": "laravel-realworld-example-app",
                    "project": "shivam-rawat-akto",
                    "lastRun": 1738939301,
                    "scheduleTime": 1738938507
                }
            ]
            // if(resp?.codeAnalysisRepos.length !== 0) {
            if(temp.length !== 0) {
                // const formattedRepos: RepoPayload[] = resp?.codeAnalysisRepos.map((x: any) => ({
                //     repo: x.repoName,
                //     project: x.projectName,
                //     lastRun: x.lastRun,
                //     scheduleTime: x.scheduleTime,
                // }));
                setReposList(temp.sort((a, b) => b.lastRun - a.lastRun));
                setSelectedConnection(id)
            }else{
                window.open("/dashboard/quick-start?connect=" + id.toLowerCase(), "_blank");
            }
        } catch (error) {
            window.open("/dashboard/quick-start?connect=" + id.toLowerCase(), "_blank");
        }
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
                                setSelectedProject(x.split("/")[1]);
                                setSelectedRepo(x.split("/")[0]);
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
                                setSelectedProject(project);
                                setSelectedRepo(repo);
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