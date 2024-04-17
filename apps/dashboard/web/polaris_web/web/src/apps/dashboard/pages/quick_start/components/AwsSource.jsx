import React, { useEffect, useState, useRef } from 'react'
import api from '../api'
import quickStartFunc from '../transform'
import { Box, Button, ProgressBar, Text, VerticalStack } from '@shopify/polaris'
import func from '@/util/func'
import Store from '../../../store'
import DropdownSearch from '../../../components/shared/DropdownSearch'
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import BannerComponent from './shared/BannerComponent'
import NoAccessComponent from './shared/NoAccessComponent'

function AwsSource() {
    const [hasRequiredAccess, setHasRequiredAccess] = useState(false)
    const [selectedLBs, setSelectedLBs] = useState([])
    const [preSelectedLBs, setPreSelectedLBs] = useState([])
    const [aktoDashboardRoleName, setAktoDashboardRoleName] = useState(null)
    const [availableLBs, setAvailableLBs] = useState([])
    const [loading, setLoading] = useState(false)
    const [statusText, setStatusText] = useState('')
    const [progressBar, setProgressBar] = useState({show: false, value: 0, max_deployment_time_in_ms: 8 * 60 * 1000})

    const [policyLines, setPolicyLines] = useState(quickStartFunc.getPolicyLines("AWS"))
    const isLocalDeploy = Store(state => state.isLocalDeploy)
    const isAws = Store(state => state.isAws)
    const DeploymentMethod = "AWS_TRAFFIC_MIRRORING"

    const ref = useRef(null)

    const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
          isActive: isActive,
          isError: isError,
          message: message
        })
    }

    const renderProgressBar = (createTime) => {
      setProgressBar(quickStartFunc.renderProgressBar(createTime, progressBar))
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
          setStatusText('Akto is tirelessly processing mirrored traffic to protect your APIs.')
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
      if(isAws){
          let intervalId = null;
          intervalId = setInterval(async () => {
            await api.fetchStackCreationStatus({deploymentMethod: DeploymentMethod}).then((resp) => {
                handleStackState(resp.stackState, intervalId)
              }
            )
          }, 5000)
      }
    }

    const fetchLBs = async() => {
      setLoading(true)
      if(isAws){
        await api.fetchLBs({deploymentMethod: DeploymentMethod}).then((resp)=> {
          if (!resp.dashboardHasNecessaryRole) {
            let policyLinesCopy = policyLines
            for (let i = 0; i < policyLines.length; i++) {
              let line = policyLinesCopy[i];
              line = line.replaceAll('AWS_REGION', resp.awsRegion);
              line = line.replaceAll('AWS_ACCOUNT_ID', resp.awsAccountId);
              line = line.replaceAll('MIRRORING_STACK_NAME', resp.aktoMirroringStackName);
              line = line.replaceAll('DASHBOARD_STACK_NAME', resp.aktoDashboardStackName);
              policyLinesCopy[i] = line;
            }
            setPolicyLines(policyLinesCopy)
          }
          setHasRequiredAccess(resp.dashboardHasNecessaryRole)
          setSelectedLBs(resp.selectedLBs);
          setPreSelectedLBs(resp.selectLBs)
          setAvailableLBs(resp.availableLBs);
          setAktoDashboardRoleName(resp.aktoDashboardRoleName); 
          setLoading(false)
          checkStackState()
        })
      }
    }

    useEffect(()=> {
      fetchLBs()
      checkStackState()
    },[])

    const docsUrl = "https://docs.akto.io/getting-started/quick-start-with-akto-self-hosted/aws-deploy"

    const urlAws = "https://us-east-1.console.aws.amazon.com/iam/home#/roles/" + aktoDashboardRoleName  + "$createPolicy?step=edit"
    const formattedJson = func.convertPolicyLines(policyLines)

    const steps = quickStartFunc.getDesiredSteps(urlAws)

    const idToNameMap = availableLBs.reduce((result, obj) => {
      result[obj.resourceId] = obj.resourceName;
      return result;
    }, {});

    const handleSelectedLBs = (selectLBs) => {
      let tempArr = quickStartFunc.getLBListFromValues(selectLBs, idToNameMap)
      setSelectedLBs(tempArr)
    }

    const copyRequest = () => {
      func.copyToClipboard(formattedJson, ref, "Policy copied to clipboard.")
    }

    const saveFunc = async() => {
      setLoading(true)
      await api.saveLBs(selectedLBs).then((resp) => {
        setLoading(false)
        setAvailableLBs(resp.availableLBs)
        setSelectedLBs(resp.selectedLBs)
        setPreSelectedLBs(resp.selectLBs)
        if (resp.isFirstSetup) {
            checkStackState()
            window.mixpanel.track("mirroring_stack_creation_initialized");
        } else {
            window.mixpanel.track("loadbalancers_updated");
        }
      })
    }

    const lbList = quickStartFunc.convertLbList(availableLBs)
    const preSelected = quickStartFunc.getValuesArr(selectedLBs)
    const noAccessText = "Your dashboard's instance needs relevant access to setup traffic mirroring, please do the following steps:"

    const noAccessObject= (loading ? {
      text: "",
      component: <SpinnerCentered/>,
      title: ""
    }:{
      text: noAccessText,
      component: <NoAccessComponent dataString={formattedJson} onClickFunc={() => copyRequest()} steps={steps} title="Policy JSON" toolTipContent="Copy JSON"/>,
      title: "NoAccess"
    })
    
    const localDeployObj = {
      text: "Use AWS packet mirroring to send duplicate stream of traffic to Akto. No performance impact, only mirrored traffic is used to analyze APIs.",
      component: <BannerComponent title="Set up Mirroring" docsUrl={docsUrl} content="To setup traffic mirroring from AWS deploy in AWS:"/>,
      title: "Local_Depoy"
    }

    const selectLBComponent = (
      loading ? 
      <SpinnerCentered />
      :
        <VerticalStack gap="2">
          <DropdownSearch itemName="load balancer" optionsList={lbList} placeholder="Select LBs to activate mirroring." 
            allowMultiple disabled={availableLBs.length === 0} preSelected={preSelected} value={`${selectedLBs.length} Load balancers selected`}
            setSelected={handleSelectedLBs}
            />

          <Box>
            <Button onClick={saveFunc} disabled={func.deepComparison(preSelectedLBs,selectedLBs)} primary loading={loading}>Apply </Button>
          </Box>
          <Text variant="bodyMd" as="h3">{statusText}</Text>
          {progressBar.show ? <ProgressBar progress={progressBar.value} size="small" color="primary" /> : null }
        </VerticalStack>
    )

    const selectedLBObj = {
      component: selectLBComponent,
      title: "Selected LB"
    }
     

    const displayObj = isLocalDeploy || !isAws ? localDeployObj : hasRequiredAccess ? selectedLBObj : noAccessObject
    
    return (
      <div className='card-items'>
        <Text>{displayObj?.text}</Text>
        {displayObj?.component}
        <div ref = {ref} />
      </div>
    )
}

export default AwsSource