import settingRequests from './api';
import func from '@/util/func';

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
      await settingRequests.fetchAktoGptConfig().then((resp)=>{
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
      await settingRequests.fetchAdminSettings().then((response)=>{
        let resp = response.accountSettings
        arr = [
          {
            title: 'Organisation',
            text: 'Akto'
          },
          {
            title: 'Organisation ID',
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
      return arr
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
    }
}

export default settingFunctions