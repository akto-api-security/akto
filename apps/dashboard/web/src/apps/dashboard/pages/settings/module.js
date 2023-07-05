import settingRequests from './api';

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
    }
}

export default settingFunctions