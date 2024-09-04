import { LegacyCard, Text, TextField, VerticalStack } from "@shopify/polaris"
import { useEffect, useState } from "react";
import api from "../api";



function BitBucketSource() {

    const [repoNames, setrepoNames] = useState('')
    const [projectName, setProjectName] = useState('')
    const [repoList, setRepoList] = useState([])
    const errorMessage = "This repository exists"

    useEffect(()=> {
        let r = []
        api.fetchCodeAnalysisRepos().then((resp) => {
            resp["codeAnalysisRepos"].forEach((x) => {
                r.push({"repo": x["repoName"], "project": x["projectName"]})
            })
            setRepoList(r)
        } )
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
                "repoName": x
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
                Use BitBucket to import your APIs
            </Text>

            <LegacyCard
                primaryFooterAction={{ content: 'Save', onAction: primaryAction, disabled: !enableButton() }}
            >
                <LegacyCard.Section>
                    <TextField onChange={(val) => setProjectName(val)} value={projectName} helpText="Name of your Project" label="Project Name" />
                    <br />
                    <TextField onChange={(val) => setrepoNames(val)} value={repoNames} label="Repo Name" helpText="This accepts comma separated values" />
                    {!isUniqueCombination() && <Text color="critical" variant="bodySm" style={{ marginTop: '8px' }}>{errorMessage}</Text>}

                </LegacyCard.Section>
            </LegacyCard>


            <VerticalStack gap="1">
                {repoList.map((repo, index) => (
                    <LegacyCard key={index} title={repo.repo} actions={[{ content: "delete", onAction: () => { handleDelete(index) } }]} sectioned={true}>
                        <Text variant="bodyMd">{repo.project}</Text>
                    </LegacyCard>
                ))}
            </VerticalStack>
        </div>
    )
}


export default BitBucketSource