import func from "@/util/func"

const transform = {
    formatJson(data){
        let allKeys = [];
        let seen = {};
        JSON.stringify(data, function (key, value) {
            if (!(key in seen)) {
                allKeys.push(key);
                seen[key] = null;
            }
            return value;
        });
        allKeys.sort();
        return JSON.stringify(data, allKeys, 2)
    },
    compareJsonKeys(original, current){
        let changedKeys = []
        let insertedKeys = []
        let deletedKeys = []
        let finalData = ""
    
        for(const key in original){
            if(!current?.hasOwnProperty(key)){
                deletedKeys.push({header: key + ":", className: 'deleted-content'})
                finalData = finalData + key + ': ' + original[key] + "\n"
            }else if (original[key] !== current[key]){
                changedKeys.push({header: key + ":", className: 'updated-content', data: original[key] + "->" + current[key], keyLength: key.length})
                finalData = finalData + key + ': ' + current[key] + "\n"
            }else{
                finalData = finalData + key + ': ' + current[key] + "\n"
            }
        }
    
        for(const key in current){
            if(!original?.hasOwnProperty(key)){
                insertedKeys.push({header: key + ":", className: 'added-content'})
                finalData = finalData + key + ': ' + current[key] + "\n"
            }
        }
    
        const mergedObject = [...deletedKeys, ...insertedKeys, ...changedKeys].reduce((result, item) => {
            result[item.header] = {className:item.className, data: item?.data, keyLength: item.keyLength};
            return result;
        }, {});
    
        finalData = finalData.split("\n").sort().join("\n") + "\n";
    
        return {
            message: finalData,
            headersMap: mergedObject,
        }
    },

    getFirstLine(original,current,originalParams,currentParams){
        let ogFirstLine = original
        let firstLine = current
        originalParams && Object.keys(originalParams).forEach((key) => {
            ogFirstLine = ogFirstLine + '?' + key + '=' + encodeURI(originalParams[key])
        })
        currentParams && Object.keys(currentParams).forEach((key) => {
            firstLine = firstLine + '?' + key + '=' + encodeURI(currentParams[key])
        })
    
        return{
            original: ogFirstLine,
            firstLine: firstLine,
            isUpdated: firstLine !== ogFirstLine
        }
    },

    processArrayJson(input) {
       try {
        let parsedJson = JSON.parse(input);
        let ret = []
        Object.keys(parsedJson).forEach((key)=>{
            if(!isNaN(key)){
                ret.push(parsedJson[key])
            }else{
                ret.push({[key]: parsedJson[key]})
            }
        })
        return JSON.stringify(ret, null, 2)
       } catch (error) {
        return input
       }
    },

    getPayloadData(original,current){

        let ret = {}
        ret.json = "ORIGINAL: \n\n " + this.formatJson(original) + "\n\nCURRENT: \n\n " + this.formatJson(current);

        return ret;
    },

    mergeDataObjs(lineObj, jsonObj, payloadObj){
        let finalMessage = (lineObj.firstLine ? lineObj.firstLine: "") + "\n" + (jsonObj.message ? jsonObj.message: "") + "\n" + this.processArrayJson(payloadObj.json)
        return{
            message: finalMessage,
            firstLine: lineObj.original + "->" + lineObj.firstLine,
            isUpdatedFirstLine: lineObj.isUpdated,
            headersMap: {...jsonObj.headersMap, ...payloadObj.headersMap},
            updatedData: jsonObj.updatedData,
        }
    },  
      
}

export default transform