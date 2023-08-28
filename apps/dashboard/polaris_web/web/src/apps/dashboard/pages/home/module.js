import homeRequests from "./api";

const homeFunctions = {
    getAllCollections: async function(){
        let allCollections = []
        await homeRequests.getCollections().then((resp)=>{
            allCollections = resp.apiCollections
        })
        return allCollections
    }
}

export default homeFunctions