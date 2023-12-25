import settingRequests from './api';
import func from '@/util/func';
import { parseString } from "xml2js"

const setupOptions = [
  { label: 'STAGING', value: 'STAGING' },
  { label: 'PROD', value: 'PROD' },
  { label: 'QA', value: 'QA' },
  { label: 'DEV', value: 'DEV' }
]

const settingFunctions = {
    getTokenList: async function (type){
        let tokensList = []
        await settingRequests.fetchApiTokens().then((resp) =>{
          resp.apiTokenList.forEach(x => {
            switch (x.utility) {
              case type:
                tokensList.push(x)
                break;
              default:
                break;
            }
          })
        })
        return tokensList
    },
    getNewToken: async function (tokenUtility){
        let tokensList = await this.getTokenList(tokenUtility)
        await settingRequests.addApiToken(tokenUtility).then((resp)=>{
            tokensList.push(...resp.apiTokenList)
            tokensList = [...tokensList]
        })
        return tokensList
    },
    deleteToken: async function(tokenId){
        await settingRequests.deleteApiToken(tokenId)
    },

    getPostmanCredentials: async function(){
      let postmanCred = {}
      await settingRequests.getPostmanCredentials().then((resp)=>{
        postmanCred = resp
      })
      return postmanCred
    },
    fetchPostmanWorkspaces: async function(postman_id){
      let workspaces = []
      await settingRequests.fetchPostmanWorkspaces(postman_id).then((resp)=>{
        workspaces = resp.workspaces
      })  
      return workspaces
    },
    addOrUpdatePostmanCred: async function(postman_id,workspace_id){
      await settingRequests.addOrUpdatePostmanCred(postman_id,workspace_id)
    },

    fetchGptCollections: async function(){
      let arr = []
      await settingRequests.fetchAktoGptConfig(-1).then((resp)=>{
        resp.currentState.forEach((collection) =>{
          if(collection.state === 'ENABLED'){
            arr.push(collection.id)
          }
        })
      })
      return arr
    },
    updateGptCollections: async function(selectedList,allCollections){
      let selectedSet = new Set(selectedList)
      const arr = allCollections.map(item => ({
				id: item.id,
				state: selectedSet.has(item.id) ? 'ENABLED' : 'DISABLED'
			}));

      await settingRequests.saveAktoGptConfig(arr)
    },
    fetchLoginInfo: async function(){
      let lastLogin = ''
      await settingRequests.fetchUserLastLoginTs().then((resp)=>{
        lastLogin = func.epochToDateTime (resp.lastLoginTs);
      })
      return lastLogin
    },
    fetchAdminInfo: async function(){
      const loginInfo = await this.fetchLoginInfo()
      let arr = []
      let resp = {}
      await settingRequests.fetchAdminSettings().then((response)=>{
        resp = JSON.parse(JSON.stringify(response.accountSettings))
        let respOrgStr = "-"
        if (response.organization) {
            let respOrg = JSON.parse(JSON.stringify(response.organization))
            respOrgStr = respOrg.id + " (" + respOrg.adminEmail + ")"
        }

        arr = [
          // {
          //   title: 'Organisation',
          //   text: 'Akto'
          // },
          {
            title: "Organization ID",
            text: respOrgStr
          },
          {
            title: 'Account ID',
            text: resp.id,
          },{
            title: 'Dashboard Version',
            text: resp.dashboardVersion,
          },{
            title: 'Runtime Version',
            text: resp.apiRuntimeVersion
          },
          {
            title: 'Last Login',
            text: loginInfo,
          },
        ]
      })
      return {arr,resp}
    },


    fetchMetricData: async function(){
      let arr = []
      await settingRequests.fetchTrafficMetricsDesciptions().then((resp)=>{
        arr = resp.names
      })
      return arr
    },
    fetchGraphData: async function(groupBy, startTimestamp, endTimestamp, names, host){
      let trafficData = {}
      await settingRequests.fetchTrafficMetrics(groupBy, startTimestamp, endTimestamp, names, host).then((resp)=>{
        trafficData = resp.trafficMetricsMap
      })
      return trafficData
    },
    testJiraIntegration: async function(userEmail, apiToken, baseUrl, projId){
      let issueType = ""
      await settingRequests.testJiraIntegration(userEmail, apiToken, baseUrl, projId).then((resp)=>{
        issueType = resp.issueType
      })
      return issueType
    },
    fetchJiraIntegration: async function(){
      let jiraInteg = {}
      await settingRequests.fetchJiraIntegration().then((resp)=>{
        jiraInteg = resp.jiraIntegration
      })
      return jiraInteg
    },
    addJiraIntegration: async function(userEmail, apiToken, baseUrl, projId, issueType){
      let trafficData = {}
      await settingRequests.addJiraIntegration(userEmail, apiToken, baseUrl, projId, issueType).then((resp)=>{
      })
      return trafficData
    },

    getSetupOptions: function(){
      return setupOptions;
    },

    getParsedXml: function(xmlText){
      let entityID = ""
      let loginUrl= ""
      let x509Certificate = ""

      parseString(xmlText, (err, result) => {
        if (err) {
          console.log(err)
          return {};
        } else {
          const entityDescriptor = result.EntityDescriptor;
          if (entityDescriptor) {
            entityID = entityDescriptor.$.entityID;
            loginUrl = entityDescriptor.IDPSSODescriptor[0].SingleSignOnService[0].$.Location;
            x509Certificate = entityDescriptor.IDPSSODescriptor[0].KeyDescriptor[0].KeyInfo[0].X509Data[0].X509Certificate[0];
          }
        }
      });

      return {
        entityId: entityID, 
        loginUrl: loginUrl,
        certificate:x509Certificate
      }
    }
}

export default settingFunctions