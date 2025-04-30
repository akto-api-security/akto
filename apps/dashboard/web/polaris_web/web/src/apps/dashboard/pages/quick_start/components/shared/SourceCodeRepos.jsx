import { HorizontalStack, LegacyCard, Text, TextField, VerticalStack } from "@shopify/polaris"
import { useEffect, useState } from "react";
import api from "../../api";
import func from "@/util/func";
import JsonComponent from './JsonComponent'

function SourceCodeRepos({type, typeName, orgName}) {

    const [repoNames, setrepoNames] = useState('')
    const [projectName, setProjectName] = useState('')
    const [repoList, setRepoList] = useState([])
    const [apiToken, setApiToken] = useState("");
    const errorMessage = "This repository exists"
    const fetchRuntimeHelmCommand = async() => {
        await api.fetchRuntimeHelmCommand().then((resp) => {
            if (!resp) return
            setApiToken(resp?.apiToken)
        })
    }

    const runtimeSvcCommand = `helm install source-code-analyser ~/akto_code/helm-charts/charts/source-code-analyser -n dev \
    --set source_code_analyser.aktoApiSecurityCodeAnalyser.env.databaseAbstractorToken="${apiToken}"`;

    const rcopyCommand = ()=>{func.copyToClipboard(runtimeSvcCommand, ref, null)}
    useEffect(()=> {
        let r = []
        api.fetchCodeAnalysisRepos(type).then((resp) => {
            resp["codeAnalysisRepos"].forEach((x) => {
                r.push({"repo": x["repoName"], "project": x["projectName"], "lastRun": x["lastRun"], "scheduleTime": x["scheduleTime"]})
            })
            setRepoList(r)
        } )
        fetchRuntimeHelmCommand()
    },[])

    const handleDelete = (index) => {
        const deleteRepo = repoList[index]

        api.deleteCodeAnalysisRepo({
            "projectName": deleteRepo["project"],
            "repoName": deleteRepo["repo"]
        })

        const updatedRepoList = repoList.filter((_, i) => i !== index);
        setRepoList(updatedRepoList);
    };

    const runRepo = (repo) => {
        repo["scheduleTime"] = func.timeNow()
        setRepoList([...repoList])
        api.runCodeAnalysisRepo([{
            "projectName": repo["project"],
            "repoName": repo["repo"]
        }])
    }

    console.log(repoList)

    const primaryAction = () => {
        const repoArray = repoNames.split(',').map(repo => repo.trim()).filter(Boolean);

        const result = repoArray.map(repo => ({
            project: projectName,
            repo: repo
        }));

        let codeAnalysisRepos = []
        repoArray.forEach((x) => {
            codeAnalysisRepos.push({
                "projectName": projectName,
                "repoName": x,
                "sourceCodeType": type
            })
        })
        api.addCodeAnalysisRepo(codeAnalysisRepos)

        setRepoList([...repoList, ...result])
        setProjectName('')
        setrepoNames('')
    }

    const isUniqueCombination = () => {
        const repoArray = repoNames.split(',').map(repo => repo.trim()).filter(Boolean);
        const existingCombinations = new Set(repoList.map(item => `${item.project}-${item.repo}`));
    
        for (const repo of repoArray) {
          const combination = `${projectName}-${repo}`;
          if (existingCombinations.has(combination)) {
            return false;
          }
        }
        return true;
      };

    const enableButton = () => {
        return projectName && repoNames && projectName.length > 0 && repoNames.length > 0 && isUniqueCombination();
    }


    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Use {typeName} to import your APIs
            </Text>

            <span>1. Run the below command to setup Source-code-analyser service: </span>

            <LegacyCard>
                <LegacyCard.Section>
                    <JsonComponent title="Source code analyser service command" toolTipContent="Copy command" onClickFunc={()=> rcopyCommand()} dataString={runtimeSvcCommand} language="text" minHeight="150px" />
                </LegacyCard.Section>
            </LegacyCard>

            <LegacyCard
                primaryFooterAction={{ content: 'Save', onAction: primaryAction, disabled: !enableButton() }}
            >
                <LegacyCard.Section>
                    <TextField onChange={(val) => setProjectName(val)} value={projectName} helpText={`Name of your ${orgName}`} label={`${orgName} Name`} />
                    <br />
                    <TextField onChange={(val) => setrepoNames(val)} value={repoNames} label="Repository Name" helpText="This accepts comma separated values" />
                    {!isUniqueCombination() && <Text color="critical" variant="bodySm" style={{ marginTop: '8px' }}>{errorMessage}</Text>}

                </LegacyCard.Section>
            </LegacyCard>


            <VerticalStack gap="1">
                {repoList.map((repo, index) => (
                    <LegacyCard 
                        key={index} 
                        title={repo.repo} 
                        actions={[
                            { content: "Delete", onAction: () => { handleDelete(index) }, destructive: true },
                            { content: "Run", onAction: () => { runRepo(repo) } },
                        ]} sectioned={true}
                    >
                        <HorizontalStack align="space-between">
                            <Text variant="bodyMd">{repo.project}</Text>
                            <Text variant="bodyMd">
                                {repo.lastRun >= repo.scheduleTime ? func.prettifyEpoch(repo.lastRun) : "scheduled"}
                            </Text>
                        </HorizontalStack>
                    </LegacyCard>
                ))}
            </VerticalStack>
        </div>
    )
}


export default SourceCodeRepos