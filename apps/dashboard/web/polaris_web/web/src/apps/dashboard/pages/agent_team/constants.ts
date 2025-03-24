import api from "./components/finalctas/api"
import { intermediateStore } from "./intermediate.store";

const STEPS_PER_AGENT_ID = {
    "FIND_VULNERABILITIES_FROM_SOURCE_CODE": 5,
    "FIND_APIS_FROM_SOURCE_CODE": 4,
    "FIND_SENSITIVE_DATA_TYPES": 1,
    "CREATE_TEST_TEMPLATES": 1,
    "GROUP_APIS": 1,
    "FIND_FALSE_POSITIVE": 1,
}

const checkForSourceCodeApis = async()=> {
    const chosenBackendDirectory = intermediateStore.getState().previousUserInput?.selectedOptions?.chosenBackendDirectory;
    await api.getSourceCodeCollectionsForDirectories({
        "chosenBackendDirectory": chosenBackendDirectory || ""
    }).then((res) => {
        intermediateStore.getState().setSourceCodeCollections(res)
    })
    return true
}

export const preRequisitesMap = {
    "FIND_VULNERABILITIES_FROM_SOURCE_CODE": {
        6: {
            "text": "Please provide the list of apis for finding vulnerabilities",
            "action": () => checkForSourceCodeApis()
        },
    }
}

const vulnerableKeys = ["IS_AUTHENTICATED", "DDOS", "BOLA", "INPUT_VALIDATION"];

export const outputKeys = {
    "FIND_VULNERABILITIES_FROM_SOURCE_CODE": vulnerableKeys
}


function toJson(input: string):any {
    const result = {};
    
    input.split(',').forEach(pair => {
      const [key, value] = pair.split(':').map(str => str.trim());
      if (key && value) {
        result[key.toLowerCase()] = value;
      }
    });
  
    return result;
}

export function structuredOutputFormat (output: any, agentType: string | undefined, subProcessId: string): any {
    switch (agentType) {
        case "FIND_VULNERABILITIES_FROM_SOURCE_CODE":
            switch (subProcessId) {
                case "1":
                    return {
                        "chosenBackendDirectory": output
                    }
                case "2":
                    if(typeof output === "string") {
                        let obj = {}
                        try {
                            obj = JSON.parse(output);
                            return obj
                        } catch (error) {
                            const jsonStr = `{${output}}`;
                            obj = JSON.parse(jsonStr);
                            return obj
                        }finally {
                            return toJson(output)  
                        }
                    }else{
                        return output
                    }
                case "3":{
                    let respArr= output.map((item: any) => {
                        let splitArr = item.split(" - ")
                        return {
                            "type": splitArr[1],
                            "file_path": splitArr[splitArr.length - 1]
                        }
                    })
                    const obj = {
                        authMethods: respArr
                    }
                    return obj
                }
                case "4": {
                    const outputOptions = intermediateStore.getState().outputOptions?.outputOptions !== undefined ? intermediateStore.getState().outputOptions?.outputOptions : intermediateStore.getState().outputOptions;
                    let valueSelectedSet = new Set(output);
                    let selectedOptions = outputOptions.filter((x: any) => valueSelectedSet.has(x?.value));
                    const obj = {
                        authMechanisms: selectedOptions
                    }
                    return obj
                }
            default:
                return output
            }
        case "FIND_APIS_FROM_SOURCE_CODE":
            switch (subProcessId) {
                case "1":
                    return {
                        "chosenBackendDirectory": output
                    }
                case "2":
                    if(typeof output === "string") {
                        const jsonStr = `{${output}}`;
                        const obj = JSON.parse(jsonStr);
                        console.log("obj", obj)
                        return obj
                    }else{
                        return output
                    }
                default:
                    return output
            }
        default:
            return output
    }
}

export default STEPS_PER_AGENT_ID