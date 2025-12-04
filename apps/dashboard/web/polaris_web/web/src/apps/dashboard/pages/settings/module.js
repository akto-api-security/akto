import settingRequests from './api';
import func from '@/util/func';
import { parseString } from "xml2js"

const setupOptions = [
  { label: 'STAGING', value: 'STAGING' },
  { label: 'PROD', value: 'PROD' },
  { label: 'QA', value: 'QA' },
  { label: 'DEV', value: 'DEV' }
]

const urlOptionsList = [
  {
    title: 'HTML',
    options: [
      {value: 'html', label: '.html'},
      {value: 'htm', label: '.htm'},
    ],
  },
  {
    title: 'CSS',
    options: [
      {value: 'css', label: '.css'},
    ],
  },
  {
    title: 'JS files',
    options: [
      {value: 'js', label: '.js'},
      {value: 'js.map', label: '.js.map'}
    ],
  },
  {
    title: 'Images',
    options: [
      {value: 'jpg', label: '.jpg'},
      {value: 'jpeg', label: '.jpeg'},
      {value: 'png', label: '.png'},
      {value: 'gif', label: '.gif'},
      {value: 'svg', label: '.svg'},
      {value: 'webp', label: '.webp'},
      {value: 'ico', label: '.ico'}
    ],
  },
  {
    title: 'Documents',
    options: [
      {value: 'json', label: '.json'},
      {value: 'pdf', label: '.pdf'},
      {value: 'doc', label: '.doc'},
      {value: 'docx', label: '.docx'},
      {value: 'xlsx', label: '.xlsx'},
      {value: 'xls', label: '.xls'},
      {value: 'pptx', label: '.pptx'},
    ],
  },
  {
    title: 'Videos',
    options: [
      {value: 'mp4', label: '.mp4'},
      {value: 'webm', label: '.webm'},
      {value: 'ogg', label: '.ogg'},
      {value: 'ogv', label: '.ogv'},
      {value: 'avi', label: '.avi'},
      {value: 'mov', label: '.mov'},
    ],
  },
  {
    title: 'Audio',
    options: [
      {value: 'mp3', label: '.mp3'},
      {value: 'wav', label: '.wav'},
      {value: 'oga', label: '.oga'},
      {value: 'ogg', label: '.ogg'},
    ],
  },
  {
    title: 'Fonts',
    options: [
      {value: 'woff', label: '.woff'},
      {value: 'woff2', label: '.woff2'},
      {value: 'ttf', label: '.ttf'},
      {value: 'otf', label: '.otf'},
    ],
  },
  {
    title: 'Content-type header',
    options: [
      { value: 'CONTENT-TYPE html', label: 'text/html'}
    ]
  },
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
    fetchRuntimeHelmCommand: async function(){
      let workspaces = []
      await settingRequests.fetchRuntimeHelmCommand().then((resp)=>{
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
      let accountSettingsDetails = {}
      await settingRequests.fetchAdminSettings().then((response)=>{
        resp = JSON.parse(JSON.stringify(response.accountSettings))
        let respOrgStr = "-"
        if (response.organization) {
            let respOrg = JSON.parse(JSON.stringify(response.organization))
            respOrgStr = respOrg.id + " (" 
            + respOrg.adminEmail + ")"
        }

        accountSettingsDetails=response.currentAccount

        if(window.IS_SAAS === "true"){
          arr = [
            {
              title: 'Account ID',
              text: resp.id,
            },
            {
              title: 'Last Login',
              text: loginInfo,
            },
          ]
        } else{
          arr = [
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
        }
      })
      return {arr,resp, accountSettingsDetails}
    },


    fetchMetricData: async function(){
      let arr = []
      await settingRequests.fetchTrafficMetricsDesciptions().then((resp)=>{
        arr = resp.names
      })
      return arr
    },
    fetchAllMetricNamesAndDescription: async function(){
      let arr = []
      await settingRequests.fetchAllMetricsDesciptions().then((resp)=>{
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
    fetchAllMetricsData: async function(startTime, endTime) {
      let metricsData = {}
      await settingRequests.fetchMetrics(startTime, endTime).then((resp) => {
        console.log("resp", resp)
        metricsData = resp?.result?.metrics
      })
      return metricsData
    },
    testJiraIntegration: async function(userEmail, apiToken, baseUrl, projId){
      let issueTypeMap = {}
      await settingRequests.testJiraIntegration(userEmail, apiToken, baseUrl, projId).then((resp)=>{
        issueTypeMap = resp
      })
      return issueTypeMap
    },
    fetchJiraIntegration: async function(){
      let jiraInteg = {}
      await settingRequests.fetchJiraIntegration().then((resp)=>{
        jiraInteg = resp
      })
      return jiraInteg
    },
    addJiraIntegration: async function(userEmail, apiToken, baseUrl, projId, projectAndIssueMap){
      let trafficData = {}
      await settingRequests.addJiraIntegration(userEmail, apiToken, baseUrl, projId, projectAndIssueMap).then((resp)=>{
      })
      return trafficData
    },
    fetchAzureBoardsIntegration: async function(){
      let azureBoardsInteg = {}
      await settingRequests.fetchAzureBoardsIntegration().then((resp)=>{
        azureBoardsInteg = resp.azureBoardsIntegration
      })
      return azureBoardsInteg
    },
    addAzureBoardsIntegration: async function(azureBoardsBaseUrl, organization, projectList, personalAuthToken) {
      let trafficData = {}
      await settingRequests.addAzureBoardsIntegration(azureBoardsBaseUrl, organization, projectList, personalAuthToken).then((resp)=>{
        trafficData = resp
      })
      return trafficData
    },
    removeAzureBoardsIntegration: async function() {
      let trafficData = {}
      await settingRequests.removeAzureBoardsIntegration().then((resp)=>{
        trafficData = resp
      })
      return trafficData
    },
    fetchServiceNowIntegration: async function(){
      let serviceNowInteg = {}
      await settingRequests.fetchServiceNowIntegration().then((resp)=>{
        serviceNowInteg = resp.serviceNowIntegration
      })
      return serviceNowInteg
    },
    fetchServiceNowTables: async function(instanceUrl, clientId, clientSecret) {
      let tables = []
      await settingRequests.fetchServiceNowTables(instanceUrl, clientId, clientSecret).then((resp)=>{
        tables = resp.tables
      })
      return tables
    },
    addServiceNowIntegration: async function(instanceUrl, clientId, clientSecret, tableNames) {
      let trafficData = {}
      await settingRequests.addServiceNowIntegration(instanceUrl, clientId, clientSecret, tableNames).then((resp)=>{
        trafficData = resp
      })
      return trafficData
    },
    removeServiceNowIntegration: async function() {
      let trafficData = {}
      await settingRequests.removeServiceNowIntegration().then((resp)=>{
        trafficData = resp
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
    },

    getRedundantUrlOptions: function(){
      let allUrls = []
      urlOptionsList.forEach((opt) => {
          opt.options.forEach((option) =>
            allUrls.push(option.value)
          );
      })
      return {
        options: urlOptionsList,
        allUrls: allUrls
      }
    },
}

export default settingFunctions