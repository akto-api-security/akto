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
                ret.push({key: parsedJson[key]})
            }
        })
        return JSON.stringify(ret, null, 2)
       } catch (error) {
        return input
       }
    },

    getPayloadData(original,current){
        let changedKeys = []
        let insertedKeys = []
        let deletedKeys = []
        let finalUnflatObj = {}

        let ogFlat = func.flattenObject(original)
        let currFlat = func.flattenObject(current)

        for(const key in ogFlat){
            let mainKey = '"' + key.split(".")?.pop() + '": ' 
            if(!currFlat?.hasOwnProperty(key)){
                let searchKey = "";
                if(typeof(ogFlat[key]) === "string"){
                    searchKey =  mainKey + '"' + ogFlat[key] + '"'
                } else if(typeof(ogFlat[key]) === 'object'){
                    searchKey = mainKey 
                } else{
                    searchKey = mainKey + ogFlat[key]
                }
                deletedKeys.push({header: searchKey, className: 'deleted-content'})
                finalUnflatObj[key] = ogFlat[key]
            }else if(!func.deepComparison(ogFlat[key],currFlat[key])){
                let searchKey = typeof(ogFlat[key]) === "string" ? mainKey + '"' + currFlat[key] + '"' : mainKey + currFlat[key]
                changedKeys.push({header:searchKey, className: 'updated-content', data: ogFlat[key] + "->" + currFlat[key], keyLength: key.split(".")?.pop().length + 2})
                finalUnflatObj[key] = currFlat[key]
            }else{
                finalUnflatObj[key] = ogFlat[key]
            }
        }

        for(const key in currFlat){
            let mainKey = '"' + key.split(".")?.pop() + '": ' 
            if(!ogFlat.hasOwnProperty(key)){
                insertedKeys.push({header: mainKey + '"' + currFlat[key] + '"', className: 'added-content'})
                finalUnflatObj[key] = currFlat[key]
            }
        }

        const mergedObject = [...deletedKeys, ...insertedKeys, ...changedKeys].reduce((result, item) => {
            result[item.header] = {className:item.className, data: item?.data, keyLength: item.keyLength};
            return result;
        }, {});

        let ret = {};
        ret.headersMap = mergedObject
        ret.json = "";

        let ogArr = typeof(original)==='string' ? original.split("\n") : []
        let curArr = typeof(current)==='string' ? current.split("\n") : [] 

        let retArr = []
        for(let i in Array.from(Array(Math.max(ogArr.length, curArr.length)))){
            if(ogArr[i] && curArr[i]){
                if(ogArr[i]!==curArr[i]){
                    ret.headersMap[curArr[i]] = {className: 'updated-content', data: ogArr[i].replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') + "->" + curArr[i].replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'), keyLength: -2}
                }
                retArr.push(curArr[i]);
            } else if(ogArr[i]) {
                ret.headersMap[curArr[i]] = {className:'deleted-content'};
                retArr.push(ogArr[i]);
            } else if(curArr[i]){
                ret.headersMap[curArr[i]] = {className:'added-content'};
                retArr.push(curArr[i]);
            }
        }

        if(typeof(current)!=='string' && typeof(original)!=='string'){
            ret.json = this.formatJson(func.unflattenObject(finalUnflatObj));
        } else if(typeof(current)==='string' && typeof(original)==='string'){
            ret.json = retArr.join("\n")
        } else if(typeof(current)==='string'){
            ret.json = retArr.join("\n") + "\n" + this.formatJson(original);
        } else {
            ret.json = this.formatJson(current) + "\n" + retArr.join("\n");
        }

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