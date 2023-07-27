import React, { useEffect, useState } from 'react'
import api from '../api'
import quickStartFunc from '../tranform'

function AwsSource() {

    const [hasRequiredAccess, setHasRequiredAccess] = useState(false)
    const [isLocalDeploy, setIsLocalDeploy] = useState(false)
    const [selectedLBs, setSelectedLBs] = useState([])
    const [existingSelectedLBs, setExistingSelectedLBs] = useState([])
    const [initialLBCount, setInitialLBCount] = useState(0)
    const [aktoDashboardRoleName, setAktoDashboardRoleName] = useState(null)
    const [availableLBs, setAvailableLBs] = useState([])

    const policyLines = quickStartFunc.getPolicyLines()

    const fetchLBs = async() => {
      if(window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'local_deploy'){
        setIsLocalDeploy(true)
      }
      else{
        await api.fetchLBs().then((resp)=> {
          if (!resp.dashboardHasNecessaryRole) {
            for (let i = 0; i < policyLines.length; i++) {
              let line = policyLines[i];
              line = line.replaceAll('AWS_REGION', resp.awsRegion);
              line = line.replaceAll('AWS_ACCOUNT_ID', resp.awsAccountId);
              line = line.replaceAll('MIRRORING_STACK_NAME', resp.aktoMirroringStackName);
              line = line.replaceAll('DASHBOARD_STACK_NAME', resp.aktoDashboardStackName);
              policyLines[i] = line;
            }
          }
          setHasRequiredAccess(resp.dashboardHasNecessaryRole)
          setSelectedLBs(resp.selectedLBs);
          for(let i=0; i<resp.availableLBs.length; i++){
              let lb = resp.availableLBs[i];
              let alreadySelected = false;
              for(let j=0; j< resp.selectedLBs.length; j++){
                  if(resp.selectedLBs[j].resourceName === lb.resourceName){
                      alreadySelected = true;
                  }
              }
              lb['alreadySelected'] = alreadySelected;
          }
          setAvailableLBs(resp.availableLBs);
          setExistingSelectedLBs(resp.selectedLBs);
          setInitialLBCount(resp.selectedLBs.length);
          setAktoDashboardRoleName(resp.aktoDashboardRoleName);
        })
      }
    }

    useEffect(()=> {
      fetchLBs()
    },[])

    return (
      <div className='card-items'>AwsSource</div>
    )
}

export default AwsSource