import React, { useEffect, useState } from 'react'
import Store from '../../../store'
import BannerComponent from './shared/BannerComponent'
import { Button, ProgressBar, Text, VerticalStack } from '@shopify/polaris'
import quickStartFunc from '../transform'
import api from '../api'
import NoAccessComponent from './shared/NoAccessComponent'
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import func from "@/util/func"
import QuickStartStore from '../quickStartStore'

function CompleteSetup({deploymentMethod, localComponentText, bannerTitle, docsUrl, bannerContent, noAccessText, setupButtonText, stackCompleteComponent}) {
    const [aktoDashboardRoleName, setAktoDashboardRoleName] = useState(null)
    const [policyLines, setPolicyLines] = useState(quickStartFunc.getPolicyLines(deploymentMethod))
    const [hasRequiredAccess, setHasRequiredAccess] = useState(false)
    const [loading, setLoading] = useState(false)
    const [stackStatus, setStackStatus] = useState("")
    const [initialClicked, setInitialClicked] = useState(false)
    const [statusText, setStatusText] = useState('')
    const [progressBar, setProgressBar] = useState({show: false, value: 0, max_deployment_time_in_ms: 8 * 60 * 1000})
    const [yaml, setYaml] = useState(quickStartFunc.getYamlLines(deploymentMethod))

    const setYamlContent = QuickStartStore(state => state.setYamlContent)

    const isLocalDeploy = Store(state => state.isLocalDeploy)
    // const isLocalDeploy = false

    const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
            isActive: isActive,
            isError: isError,
            message: message
        })
    }

    const renderProgressBar = (createTime) => {
        setProgressBar(quickStartFunc.renderProgressBar(createTime))
        }
    
        const removeProgressBarAndStatuschecks = (intervalId) => {
        clearInterval(intervalId)
        setProgressBar(quickStartFunc.removeProgressBarAndStatuschecks(progressBar))
        }
    

    const handleStackState = (stackState, intervalId) => {
        switch (stackState.status) {
            case 'CREATE_IN_PROGRESS':
            renderProgressBar(stackState.creationTime);
            setStatusText('We are setting up mirroring for you! Grab a cup of coffee, sit back and relax while we work our magic!')
            break;
            case 'CREATE_COMPLETE':
            removeProgressBarAndStatuschecks(intervalId);
            break;
            case 'DOES_NOT_EXISTS':
            removeProgressBarAndStatuschecks(intervalId);
            setStatusText('Mirroring is not set up currently, choose 1 or more LBs to enable mirroring.')
            break;
            case 'TEMP_DISABLE':
            removeProgressBarAndStatuschecks(intervalId)
            setStatusText('Current deployment is in progress, please refresh this page in sometime.')
            break;
            default:
            removeProgressBarAndStatuschecks(intervalId);
            setStatusText('Something went wrong while setting up mirroring, please write to us at support@akto.io')
        }      
        }

    const checkStackState = () => {
        let intervalId = null;
        intervalId = setInterval(async () => {
            await api.fetchStackCreationStatus({deploymentMethod: deploymentMethod}).then((resp) => {
                setStackStatus(resp.stackState.status)
                handleStackState(resp.stackState, intervalId)
                if(resp.aktoNLBIp && resp.aktoMongoConn){
                    let yamlCopy = yaml
                    for(let i=0; i< yaml.length; i++){
                        let line = yamlCopy[i];
                        line = line.replace('<AKTO_NLB_IP>', resp.aktoNLBIp);
                        line = line.replace('<AKTO_MONGO_CONN>', resp.aktoMongoConn);
                        yamlCopy[i] = line;
                    }
                    setYaml(yaml)
                }
            })
        }, 5000)
    }

    const fetchLBs = async() => {
        if(!isLocalDeploy){
            setLoading(true)
            await api.fetchLBs({deploymentMethod: deploymentMethod}).then((resp) => {
                if (!resp.dashboardHasNecessaryRole) {
                    let policyLinesCopy = policyLines
                    for (let i = 0; i < policyLines.length ; i++) {
                        let line = policyLinesCopy[i];
                        line = line.replaceAll('AWS_REGION', resp.awsRegion);
                        line = line.replaceAll('AWS_ACCOUNT_ID', resp.awsAccountId);
                        policyLinesCopy[i] = line;
                    }
                    setPolicyLines(policyLinesCopy)
                }
                setHasRequiredAccess(resp.dashboardHasNecessaryRole)
                // setHasRequiredAccess(true)
                setAktoDashboardRoleName(resp.aktoDashboardRoleName); 
                setLoading(false)
                checkStackState()
            })
        }
    } 

    useEffect(() => {
        fetchLBs()
    },[])

    useEffect(() => {
        const yamlContent = yaml.join('\n')
        setYamlContent(yamlContent)
    }, [yaml])

    const urlFargate = "https://us-east-1.console.aws.amazon.com/iam/home#/roles/" + aktoDashboardRoleName  + "$createPolicy?step=edit";

    const steps = quickStartFunc.getDesiredSteps(urlFargate)
    const formattedJson = func.convertPolicyLines(policyLines)

    const copyRequest = () => {
        navigator.clipboard.writeText(formattedJson)
        setToast(true, false, "Policy copied to clipboard.")
    }
    
    const creatFargateStack = async() => {
        setLoading(true)
        setStatusText("Starting Deployment!!")
        setInitialClicked(true)
        await api.createRuntimeStack().then((resp)=> {
        setLoading(false)
        checkStackState()
        })
    }

    const checkConditions = () => {
        return (stackStatus === "CREATE_COMPLETE" || stackStatus === "CREATE_IN_PROGRESS" || stackStatus === "CREATION_FAILED")
    }

    const buttonActive = () => {
        const conditions = checkConditions()
        return !(initialClicked || conditions)
    }

    const isButtonActive = buttonActive()

    const accessComponent = (
        loading ? 
        <SpinnerCentered />
        :
        <VerticalStack gap="2">
            {isButtonActive ? <Button primary onClick={creatFargateStack} loading={loading}>{setupButtonText}</Button> : null}
            <Text variant="bodyMd" as="h3">{statusText}</Text>
            {progressBar.show ? <ProgressBar progress={progressBar.value} size='medium' /> : null }
            {/* {stackCompleteComponent} */}
            {stackStatus === "CREATE_COMPLETE" ?
                {stackCompleteComponent}
                : null
            }
        </VerticalStack>
    )

    const localDeployObj = {
        text:  localComponentText,
        component: <BannerComponent title={bannerTitle} docsUrl={docsUrl} content={bannerContent} />
    }

    const noAccessObject = {
        text: noAccessText,
        component: <NoAccessComponent  dataString={formattedJson} onClickFunc={() => copyRequest()} steps={steps} title="Policy JSON" toolTipContent="Copy JSON"/>
    }

    const accessObj = {
        component: accessComponent
    }

    const displayFunc = () => {
        if (isLocalDeploy) {
            return localDeployObj
        }
        if (hasRequiredAccess) {
            return accessObj
        }
        return noAccessObject
    }

    const displayObj = displayFunc();

    return (
        <div className='card-items'>
            <Text>{displayObj?.text}</Text>
            {displayObj?.component}
        </div>
    )
}

export default CompleteSetup