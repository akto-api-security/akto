import React, { useEffect, useState } from 'react'
import api from '../api'
import quickStartFunc from '../tranform'
import { Avatar, Banner, Box, Button, HorizontalStack, LegacyCard, ProgressBar, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import {ClipboardMinor} from "@shopify/polaris-icons"
import func from '@/util/func'
import SampleData from '../../../components/shared/SampleData'
import { useNavigate } from 'react-router-dom'
import QuickStartStore from '../quickStartStore'
import Store from '../../../store'
import DropdownSearch from '../../../components/shared/DropdownSearch'
import SpinnerCentered from "../../../components/progress/SpinnerCentered"

function AwsSource() {
    const [hasRequiredAccess, setHasRequiredAccess] = useState(true)
    const [selectedLBs, setSelectedLBs] = useState([])
    const [preSelectedLBs, setPreSelectedLBs] = useState([])
    const [aktoDashboardRoleName, setAktoDashboardRoleName] = useState(null)
    const [availableLBs, setAvailableLBs] = useState([])
    const [loading, setLoading] = useState(false)
    const [statusText, setStatusText] = useState('')
    const [progressBar, setProgressBar] = useState({show: false, value: 0, max_deployment_time_in_ms: 8 * 60 * 1000})

    const [policyLines, setPolicyLines] = useState(quickStartFunc.getPolicyLines())
    const active = QuickStartStore(state => state.active)
    const navigate = useNavigate()
    const isLocalDeploy = Store(state => state.isLocalDeploy)

    const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
          isActive: isActive,
          isError: isError,
          message: message
        })
    }

    const renderProgressBar = (creationTimeInMs) => {
      const progressBarCopy = JSON.parse(JSON.stringify(progressBar))
      progressBarCopy.show = true;
      const currTimeInMs = Date.now();
      const maxDeploymentTimeInMs = progressBarCopy.max_deployment_time_in_ms;
      let progressPercent = ((currTimeInMs - creationTimeInMs) * 100) / maxDeploymentTimeInMs
      if (progressPercent > 90) {
          progressPercent = 90;
      }
      // to add more else if blocks to handle cases where deployment is stuck
      progressBarCopy.value = Math.round(progressPercent);
      setProgressBar(progressBarCopy)
    }

    const removeProgressBarAndStatuschecks = (intervalId) => {
      const progressBarCopy = JSON.parse(JSON.stringify(progressBar))
      progressBarCopy.show = false;
      progressBarCopy.value = 0;
      setProgressBar(progressBarCopy)
      clearInterval(intervalId);
    }

    const handleStackState = (stackState, intervalId) => {
      switch (stackState.status) {
        case 'CREATE_IN_PROGRESS':
          renderProgressBar(stackState.creationTime);
          setStatusText('We are setting up mirroring for you! Grab a cup of coffee, sit back and relax while we work our magic!')
          break;
        case 'CREATE_COMPLETE':
          removeProgressBarAndStatuschecks(intervalId);
          setStatusText('Akto is tirelessly processing mirrored traffic to protect your APIs. Click <a class="clickable-docs" href="/dashboard/observe/inventory">here</a> to navigate to API Inventory.')
          break;
        case 'DOES_NOT_EXISTS':
          removeProgressBarAndStatuschecks(intervalId);
          setStatusText('Mirroring is not set up currently, choose 1 or more LBs to enable mirroring.')
          break;
        default:
          removeProgressBarAndStatuschecks(intervalId);
          setStatusText('Something went wrong while setting up mirroring, please write to us at support@akto.io')
      }      
    }

    const checkStackState = () => {
      let intervalId = null;
      intervalId = setInterval(async () => {
        await api.fetchStackCreationStatus().then((resp) => {  
            handleStackState(resp.stackState, intervalId)
          }
        )
      }, 5000)
    }

    const fetchLBs = async() => {
      setLoading(true)
      if(!isLocalDeploy){
        await api.fetchLBs().then((resp)=> {
          if (!resp.dashboardHasNecessaryRole) {
            let policyLinesCopy = policyLines
            for (let i = 0; i < policyLinesCopy.length; i++) {
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
      if(!isLocalDeploy && !hasRequiredAccess && active === "update"){
        navigate("/dashboard/quick-start/aws-setup")
      }else{
        navigate("/dashboard/quick-start")
      }
    },[])

    const docsUrl = "https://docs.akto.io/getting-started/quick-start-with-akto-self-hosted/aws-deploy"
    const openLink = (url)=> {
      window.open(url)
    }

    const urlAws = "https://us-east-1.console.aws.amazon.com/iam/home#/roles/" + aktoDashboardRoleName  + "$createPolicy?step=edit"
    const formattedJson = func.convertPolicyLines(policyLines)
    const dataObj = {
      json: formattedJson,
    }

    const steps = [
      {
        text: "Grab the policy JSON below and navigate to Akto Dashboard's current role by clicking ",
        textComponent: <a target='_blank' href={urlAws}>here</a>, 
      },
      {
        text: "We will create an inline policy, navigate to JSON tab and paste the copied JSON here."
      },
      {
        text: "Click on 'Review policy'."
      },
      {
        text: "Now lets name the policy as 'AktoDashboardPolicy'."
      },
      {
        text: "Finally create the policy by clicking on 'Create policy'."
      },
    ]

    const idToNameMap = availableLBs.reduce((result, obj) => {
      result[obj.resourceId] = obj.resourceName;
      return result;
    }, {});

    const handleSelectedLBs = (selectLBs) => {
      let tempArr = quickStartFunc.getLBListFromValues(selectLBs, idToNameMap)
      setSelectedLBs(tempArr)
    }

    const copyRequest = () => {
      let jsonString  = JSON.stringify(formattedJson, null, 2)
      navigator.clipboard.writeText(jsonString)
      setToast(true, false, "Policy copied to clipboard.")
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

    const noAccessComponent = (
      <VerticalStack gap="1">
          {steps.map((element,index) => (
              <VerticalStack gap="1" key={index}>
                  <HorizontalStack gap="1" wrap={false} key={element.text}>
                      <span>{index + 1}.</span>
                      <span>{element.text}</span>
                      <span>{element.textComponent}</span>
                  </HorizontalStack>
                  <HorizontalStack gap="3">
                      <div/>
                      {/* <SampleData data={dataObj} /> */}
                      {element?.component}
                  </HorizontalStack>
              </VerticalStack>
          ))}
          <span>6. Click <Button plain onClick={() => navigate(0)}>here</Button> to refresh.</span>
      </VerticalStack>
    )
    const noAccessObject= {
      text: "Your dashboard's instance needs relevant access to setup traffic mirroring, pleasedo the following steps:",
      component: noAccessComponent,
      title: "NoAccess"
    }
    
    const localDeployComponent = (
      <div>
        <Banner title='Set Up Mirroring' status='warning'>
          <span>To setup traffic mirroring from AWS deploy in AWS:</span>
          <br/>
          <Button plain onClick={() => openLink(docsUrl)}>Go to docs</Button>
        </Banner>
      </div>
    )
    const localDeployObj = {
      text: "Use AWS packet mirroring to send duplicate stream of traffic to Akto. No performance impact, only mirrored traffic is used to analyze APIs.",
      component: localDeployComponent,
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
          {progressBar.show ? <ProgressBar progress={progressBar.value} size='medium' /> : null }
        </VerticalStack>
    )

    const selectedLBObj = {
      component: selectLBComponent,
      title: "Selected LB"
    }
     

    const displayObj = isLocalDeploy ? localDeployObj : hasRequiredAccess ? selectedLBObj : noAccessObject
    const headerTitle = (
      <HorizontalStack gap="3">
        <Avatar customer size="medium" name="AWS Logo" source="/public/aws.svg"/>
        <Text variant='headingMd' as='h5'>
          AWS Setup guide
        </Text>
      </HorizontalStack>
    )
    return (
      active === 'update' && displayObj.title === 'NoAccess' ?
        <div style={{marginTop: '2vw', padding: '0 4vw'}}>
          <LegacyCard title={headerTitle}>
            <LegacyCard.Section>
              <Text>{displayObj.text}</Text>
              <br/>
              {displayObj.component}
            </LegacyCard.Section>
            <LegacyCard.Section>
              <div className='copyRequest'>
                <Text>Policy JSON</Text>
                <Tooltip dismissOnMouseOut preferredPosition='above' content="Copy JSON">
                <Button icon={ClipboardMinor} plain onClick={()=> copyRequest()} />
                </Tooltip>
              </div>
              <SampleData data={dataObj} />
            </LegacyCard.Section>
          </LegacyCard>
        </div>
      :
        <div className='card-items'>
          <Text>{displayObj?.text}</Text>
          {displayObj?.component}
          {displayObj.title === "NoAccess" ?
          <VerticalStack gap="1">  
            <div className='copyRequest'>
              <Text>Policy JSON</Text>
              <Tooltip dismissOnMouseOut preferredPosition='above' content="Copy JSON">
                <Button icon={ClipboardMinor} plain  />
              </Tooltip>
            </div>
            <SampleData data={dataObj} />
          </VerticalStack>
           : null }
        </div>
       
    )
}

export default AwsSource