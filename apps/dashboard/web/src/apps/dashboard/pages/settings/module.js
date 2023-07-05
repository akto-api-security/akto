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
        await settingRequests.deleteApiToken(tokenId).then((resp)=>{
            console.log(resp)
        })
    }
}

export default settingFunctions