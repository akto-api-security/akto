import settingRequests from './api';
import globalFunctions from '@/util/func';

const settingFunctions = {
    getTokenList: async function (){
        let burp_tokens = []
        await settingRequests.fetchApiTokens().then((resp) =>{
          resp.apiTokenList.forEach(x => {
            switch (x.utility) {
              case globalFunctions.testingResultType().BURP:
                burp_tokens.push(x)
                break;
              default:
                break;
            }
          })
        })
        return burp_tokens
    },
    getNewToken: async function (tokenUtility){
        let burp_tokens = await this.getTokenList()
        await settingRequests.addApiToken(tokenUtility).then((resp)=>{
            burp_tokens.push(...resp.apiTokenList)
            burp_tokens = [...burp_tokens]
        })
        return burp_tokens
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