import { Box, Button, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'
import SSOTextfield from '../../../../signup/components/SSOTextfield'
import {  RepoType } from '../types'
import func from "../../../../../util/func"
import agentApi from '../api'
import { useAgentsStore } from '../agents.store'
import DropdownSearch from '../../../components/shared/DropdownSearch'

const getProjectObj = (connection: string) => {
    switch (connection) {
        case 'GITHUB':
            return {
                "1": {
                    label: "Organization/User Name",
                    placeholder: "Enter organization/user name",
                },
                "2" : {
                    label: 'Repository Name',
                    placeholder: "Enter repository name",
                }
            }
        case 'BITBUCKET':
            return {
                "1": {
                    label: "Workspace/Team Name",
                    placeholder: "Enter WorkSpace/team name",
                },
                "2" : {
                    label: 'Repository Name',
                    placeholder: "Enter repository name",
                }
            }
        case 'AZUREDEVOPS':
            return {
                "1": {
                    label: "Organization Name",
                    placeholder: "Enter organization name",
                },
                "2": {
                    label: "Project name",
                    placeholder: "Enter Project name",
                },
                "3": {
                    label: "Repository name",
                    placeholder: "Enter repository name",
                }
            }
        case 'GITLAB':
            return {
                "1": {
                    label: "Project ID",
                    placeholder: "Enter project id",
                }
            }
    }   
}

async function checkRepoReadAccess({ platform, projectName, repoName, repoNameAzure, privateToken }): Promise<{ success: boolean; reason?: string }> {
    try {
      let url = '';
      let headers = {};
  
      switch (platform.toLowerCase()) {
        /**
         * GitHub API
         * URL Format: https://api.github.com/repos/{owner}/{repo}
         * API Docs: https://docs.github.com/en/rest/repos/repos#get-a-repository
        */
        case 'github':
          url = `https://api.github.com/repos/${projectName}/${repoName}`;
          headers = privateToken ? { Authorization: `Bearer ${privateToken}` } : {};
          break;
        /**
         * Bitbucket API
         * URL Format: https://api.bitbucket.org/2.0/repositories/{workspace}/{repo_slug}
         * API Docs: https://developer.atlassian.com/cloud/bitbucket/rest/api-group-repositories/#api-repositories-workspace-repo-slug-get
        */
        case 'bitbucket':
          url = `https://api.bitbucket.org/2.0/repositories/${projectName}/${repoName}`;
          headers = privateToken
            ? {
                Authorization: `Bearer ${privateToken}`,
              }
            : {};
          break;
        /**
         * Azure DevOps API
         * URL Format: https://dev.azure.com/{organization}/{project}/_apis/git/repositories/{repositoryName}?api-version=4.1
         * API Docs: https://learn.microsoft.com/en-us/rest/api/azure/devops/git/repositories/get?view=azure-devops-rest-4.1&tabs=HTTP
        */
        case 'azuredevops':
            url = `https://dev.azure.com/${projectName}/${repoName}/_apis/git/repositories/${repoNameAzure}?api-version=4.1`;
            headers = privateToken 
              ? {
                    Authorization: `Bearer ${privateToken}`,
                }
               : {}; 
            break;
        /**
        * GitLab API
       * URL Format: https://gitlab.com/api/v4/projects/{project_id}
       * API Docs: https://docs.gitlab.com/api/projects/#get-a-single-project
       */
        case 'gitlab':
            url = `https://gitlab.com/api/v4/projects/${projectName}`;
            headers = privateToken
              ? {
                    'PRIVATE-TOKEN': `${privateToken}`,
                }
                : {};
            break;
        default:
          throw new Error('Unsupported platform');
      }
  
      const response = await fetch(url, { headers });
  
      if (response.status === 200) {
        return { success: true };
      } else if (response.status === 401 || response.status === 203) {
        return { success: false, reason: `Pass correct Access Token`};
      } else if (response.status === 404 || response.status === 403) {
        return { success: false, reason: `Access denied or repository not found.` };
      } else {
        const text = await response.text();
        return { success: false, reason: `Unexpected response: ${response.status} - ${text}` };
      }
    } catch (err) {
      return { success: false, reason: err.message };
    }
  }
  

function RepoSelector({ handleClickRepo, selectedConnection }) {
    const [newRepoName, setNewRepoName] = useState<string>('');   // organization_name, 
    const [newProjectName, setNewProjectName] = useState<string>('');   // project_name,
    const [newRepoNameAzure, setNewRepoNameAzure] = useState<string>('');   // repo_name
    const [githubAccessToken, setGithubAccessToken] = useState<string | null>(null);
    const [allRepos, setAllRepos] = useState<any[]>([]);
    const [manualType, setManualType] = useState<boolean>(selectedConnection !== 'GITHUB');
    const [invalidInput, setInvalidInput] = useState<boolean>(false);
    const [loading, setLoading] = useState<boolean>(false);

    const getAllReposForProject = async(accessToken: string | null, project: string) => {
        setLoading(true);
        const GITHUB_API_URL = 'https://api.github.com/graphql';
        const query = `
        query {
            organization(login: "${project}") {
                repositories(first: 100) {
                    nodes {
                        name
                        nameWithOwner
                        isPrivate
                    }
                }
            }
        }
    `;
      try {
        const response = await fetch(GITHUB_API_URL, {
            method: 'POST',
            headers: accessToken && accessToken.length > 0 ? {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${accessToken}`,
            }: {'Content-Type': 'application/json'},
            body: JSON.stringify({ query }),
            });

            const result = await response.json();

            if (result.errors) {
                func.setToast(true, true, result.errors[0].message);
                setInvalidInput(true);
            } else {
                const nodes = result.data.organization?.repositories?.nodes ?? [];
                setInvalidInput(nodes.length === 0);
                setLoading(false);
                setTimeout(() => {
                    setAllRepos(nodes);
                },200)
            }
            setLoading(false);
           
        } catch (err: any) {
            setInvalidInput(true);
            setLoading(false);
        }
    };

    const handleAddRepository = async (projectName: string | null) => {
        const project = projectName || newProjectName;
        if (newRepoName) {
            setLoading(true);
            await checkRepoReadAccess({
                platform: selectedConnection.toLowerCase(),
                projectName: newRepoName,
                repoName: project,
                repoNameAzure: newRepoNameAzure,
                privateToken: githubAccessToken
            }).then(async (res) => {
                if (res.success) {
                    setInvalidInput(false);
                    await handleClickRepo(newRepoName, project, null, githubAccessToken, newRepoNameAzure);
                    setNewRepoName('');
                    setNewProjectName('');
                    setNewRepoNameAzure('');
                    setGithubAccessToken('');
                    func.setToast(true, false, "Repository added successfully");
                } else {
                    setInvalidInput(true);
                    func.setToast(true, true, res.reason || "Error checking repository access");
                }
            }).catch((err) => {
                setInvalidInput(true);
                func.setToast(true, true, err.message || "Error checking repository access");
            });
            setLoading(false);
        }
    };

    const projectObj = getProjectObj(selectedConnection);
    

    const isAzureDevops = (selectedConnection === 'AZUREDEVOPS');
    const isGitLab = (selectedConnection === 'GITLAB');

    return (
        <Box paddingInlineEnd={"2"} paddingInlineStart={"2"}>
            <VerticalStack gap={"4"}>
                {selectedConnection === 'GITHUB' ? <Box paddingBlockStart={"5"} width='250px'>
                    <Button onClick={() => setManualType(!manualType)} monochrome disclosure>
                        {manualType ? "Hide Manual Input" : "Show Manual Input"}
                    </Button>
                </Box>: null}
                {/* New repository input section */}
                <VerticalStack gap={"2"}>
                    <Text variant="bodyMd" as='span'>
                        Add a new repository:
                    </Text>
                    <HorizontalStack gap={"4"} align='space-between'>
                    <HorizontalStack gap={"2"} align="start">
                        <Box width='200px'>
                            <TextField
                                label={projectObj?.[1]?.label || "Project Name"}
                                autoComplete="off"
                                value={newRepoName}
                                onChange={(value) => setNewRepoName(value)}
                                placeholder={projectObj?.[1]?.placeholder || "Enter project Name"}
                                requiredIndicator
                                error={invalidInput}
                            />
                        </Box>
                        {manualType && !isGitLab ? <Box width='200px'>
                            <TextField
                                label={projectObj?.[2]?.label || "Repository Name"}
                                autoComplete="off"
                                value={newProjectName}
                                onChange={(value) => setNewProjectName(value)}
                                placeholder={projectObj?.[2]?.placeholder || "Enter repository Name"}
                                requiredIndicator
                                error={invalidInput}
                            />
                        </Box> : null}
                        {isAzureDevops && (
                            <Box width='200px'>
                                <TextField
                                    label={projectObj?.[3]?.label || "Azure Repository Name"}
                                    autoComplete="off"
                                    value={newRepoNameAzure}
                                    onChange={(value) => setNewRepoNameAzure(value)}
                                    placeholder={projectObj?.[3]?.placeholder || "Enter Azure Repository Name"}
                                    requiredIndicator
                                    error={invalidInput}
                                />
                            </Box>
                        )}
                        <Box width='200px'>
                            <TextField
                                label="Access Token"
                                autoComplete="off"
                                value={githubAccessToken || ""}
                                onChange={(value) => setGithubAccessToken(value)}
                                placeholder="Enter access token in case of private repo"
                                requiredIndicator={selectedConnection !== 'GITHUB'}
                                error={invalidInput}
                            />
                        </Box>
                    </HorizontalStack>
                        <Box width='200px' paddingBlockStart={"6"} paddingInlineStart={"4"}>
                            <Button loading={loading} onClick={() => manualType ? handleAddRepository(null) : getAllReposForProject(githubAccessToken, newRepoName)} disabled={manualType ? (!newRepoName || (!isGitLab && !newProjectName) || (isAzureDevops && !newRepoNameAzure) || !githubAccessToken): (!newRepoName)} primary>
                                {manualType ? "Add Repository" : "Add Project"}
                            </Button>
                        </Box>
                    </HorizontalStack>
                </VerticalStack>
                {allRepos.length > 0 ?
                    <Box width='300px'>
                        <DropdownSearch
                            optionsList={allRepos?.map((repo: any) => {
                                // TODO: optionally take this function for transformation.
                                return {
                                    label: `${repo.nameWithOwner} (${repo.isPrivate ? "Private" : "Public"})`, 
                                    value: repo?.name,
                                }
                            })}
                            placeHolder={"Edit choice(s)"}
                            setSelected={(selectedChoices: any) => {setNewProjectName(selectedChoices); handleAddRepository(selectedChoices)}}
                    />
                    </Box>: null
                }
            </VerticalStack>
            
        </Box>
    )
}

function RepositoryInitializer({ agentType }: { agentType: string }) {
    const [selectedConnection, setSelectedConnection] = React.useState<string>('')
    const [selectedRepo, setSelectedRepo] = React.useState<string>('')
    const [selectedProject, setSelectedProject] = React.useState<string>('')
    const [temp, setTemp] = React.useState<string>('')
    const {selectedModel} = useAgentsStore(state => state)

    const handleClick = async (id: string) => {
        try {
            setSelectedConnection(id)
        } catch (error) {
            window.open("/dashboard/quick-start?connect=" + id.toLowerCase(), "_blank");
        }
    }

    const handleClickRepo = async (repo: string, project: string, localString: string | null, accessToken: string|null, repoNameAzure: string|null) => {
        setSelectedProject(project);
        setSelectedRepo(repo);

        const data = localString !== null
            ? { projectDir: localString }
            : selectedConnection && repo 
                ? {
                    sourceCodeType: selectedConnection,
                    repository: repo,
                    project: project,
                    repoNameAzure: repoNameAzure
                }
                : {};
        
        if (Object.keys(data).length === 0) return;
        if(accessToken !== null) {
            data['accessToken'] = accessToken;
        }

        await agentApi.createAgentRun({ 
            agent: agentType, 
            data,
            githubAccessToken: accessToken,
            modelName: selectedModel?.id
         });
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
            id: 'BITBUCKET',
            logo: '/public/bitbucket.svg',
            text: 'Continue with BitBucket',
            onClickFunc: () => handleClick('BITBUCKET')
        },
        {
            id: 'AZUREDEVOPS',
            logo: '/public/azure.svg',
            text: 'Continue with AzureDevops',
            onClickFunc: () => handleClick('AZUREDEVOPS')
        },
        {
            id: 'GITLAB',
            logo: '/public/gitlab.svg',
            text: 'Continue with GitLab',
            onClickFunc: () => handleClick('GITLAB')
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
                                    connectedRight={<Button onClick={() => handleClickRepo("", "", temp, null, null)}>Start</Button>}
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
            selectedConnection={selectedConnection}
        />
    )
}

export default RepositoryInitializer