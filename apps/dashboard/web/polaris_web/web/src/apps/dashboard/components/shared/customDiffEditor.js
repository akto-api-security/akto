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

    getFirstLine(original,current){
        return{
            original: original,
            firstLine: current,
            isUpdated: original !== current
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

    getPayloadData(original, current) {

        let res = {
            headersMap: {},
            json: ""
        }

        let ogType = typeof (original)
        let curType = typeof (current)

        if (ogType === 'object' && curType === 'object' && !Array.isArray(original) && !Array.isArray(current)) {

            // object diff
            let final = { ...original, ...current };
            var diff = require('deep-diff').diff;
            var differences = diff(original, current);
            // console.log(original, current, differences)
            res.json = this.formatJson(final);
            if (differences != undefined) {
                for (let diff of differences) {
                    let kind = diff.kind;
                    if(kind == undefined){
                        continue;
                    }
                    let path = diff.path;
                    if(path == undefined){
                        continue;
                    }
                    let key = diff.path.pop();
                    if(key==undefined){
                        continue;
                    }
                    let searchKey = '"' + key + '": ';
                    switch (kind) {
                        case 'N':
                            searchKey = this.getSearchKey(searchKey, diff.rhs);
                            searchKey.split("\n").forEach((line) => {
                                res.headersMap[this.escapeRegex(line)] = { className: 'added-content' }
                            })
                            break;
                        case 'D':
                            searchKey = this.getSearchKey(searchKey, diff.lhs);
                            searchKey.split("\n").forEach((line) => {
                                res.headersMap[this.escapeRegex(line)] = { className: 'deleted-content' }
                            })
                            break;
                        case 'A':
                            let ogTemp = original;
                            let curTemp = current;
                            for (let i in diff.path) {
                                ogTemp = ogTemp[diff.path[i]];
                                curTemp = curTemp[diff.path[i]];
                            }
                            let data = this.formatJson(ogTemp) + "->" + this.formatJson(curTemp);
                            res.headersMap[searchKey] = { className: 'updated-content', keyLength: key.length + 2, data: data }
                            break;
                        case 'E':
                            let data2 = this.formatJson(diff.lhs) + "->" + this.formatJson(diff.rhs);
                            res.headersMap[searchKey] = { className: 'updated-content', keyLength: key.length + 2, data: data2 }
                            break;
                        default:
                            break;
                    }
                }
            }

        } else if (ogType === 'string' && curType === 'string') {

            // string diff.
            let ogArr = typeof (original) === 'string' ? original.split("\n") : []
            let curArr = typeof (current) === 'string' ? current.split("\n") : []

            let retArr = []
            for (let i in Array.from(Array(Math.max(ogArr.length, curArr.length)))) {
                if (ogArr[i] && curArr[i]) {
                    if (ogArr[i] !== curArr[i]) {
                        res.headersMap[this.escapeRegex(curArr[i])] = { className: 'updated-content', data: ogArr[i].replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;') + "->" + curArr[i].replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'), keyLength: -2 }
                    }
                    retArr.push(curArr[i]);
                } else if (ogArr[i]) {
                    res.headersMap[this.escapeRegex(curArr[i])] = { className: 'deleted-content' };
                    retArr.push(ogArr[i]);
                } else if (curArr[i]) {
                    res.headersMap[this.escapeRegex(curArr[i])] = { className: 'added-content' };
                    retArr.push(curArr[i]);
                }
            }
            res.json = retArr.join("\n")

        } else {
            // handle mix match. array, string, object.

            let og = this.standardDiff(original)
            let cur = this.standardDiff(current)
            if(og === cur){
                res.json = cur
            }
            else {
                res.json = og + "\n" + cur
                og.split("\n").forEach((line) => {
                    res.headersMap[this.escapeRegex(line)] = { className: 'deleted-content' }
                })
                cur.split("\n").forEach((line) => {
                    res.headersMap[this.escapeRegex(line)] = { className: 'added-content' }
                })
            }
            
        }

        return res;
    },

    standardDiff(data) {

        if(data == null || data == undefined){
            return "";
        }    

        let type = typeof (data)

        if (type === 'string' || type === 'number' || type === 'boolean') {
            return data.toString();
        } else if (type === 'object') {
            if (Array.isArray(data)) {
                return this.processArrayJson(this.formatJson(data))
            } else {
                return this.formatJson(data)
            }
        } else {
            return data.toString();
        }
    },

    escapeRegex(string) {
        return string;
    },

    getSearchKey(mainKey, value) {
        let searchKey = "";
        if (typeof (value) === "string") {
            searchKey = mainKey + '"' + value + '"'
        } else if (typeof (value) === 'object') {
            if (Array.isArray(value)) {
                searchKey = mainKey + this.processArrayJson(this.formatJson(value))
            } else {
                searchKey = mainKey + this.formatJson(value)
            }
        } else {
            searchKey = mainKey + value
        }
        return searchKey;
    },

    mergeDataObjs(lineObj, jsonObj, payloadObj){
        let finalMessage = (lineObj.firstLine ? lineObj.firstLine: "") + "\n" + (jsonObj.message ? jsonObj.message: "") + "\n" + payloadObj.json
        return{
            message: finalMessage,
            firstLine: lineObj.original + "->" + lineObj.firstLine,
            isUpdatedFirstLine: lineObj.isUpdated,
            headersMap: {...jsonObj.headersMap, ...payloadObj.headersMap},
            updatedData: jsonObj.updatedData,
        }
    },
    formatData(data,style){
        let localFirstLine = data?.firstLine
        let finalData = ""
        let payLoad = null
        if(style === "http" && data && Object.keys(data).length > 0){
            if(data.json){
                Object.keys(data?.json).forEach((element)=> {
                    if(element.includes("Header")){
                        if(data.json[element]){
                            Object.keys(data?.json[element]).forEach((key) => {
                                finalData = finalData + key + ': ' + data.json[element][key] + "\n"
                            })
                        }
                    }else{
                        payLoad = data.json[element]
                    }
                })
            }
            finalData = finalData.split("\n").sort().join("\n");
            const isPayloadEmpty = payLoad === null || Object.keys(payLoad).length === 0
            const isMultiformData = data?.json?.requestHeaders?.['content-type']?.includes('multipart/form-data') && (payLoad == null || Object.keys(payLoad).length === 0)
            return (localFirstLine + "\n" + finalData + (finalData.trim().length === 0 || isPayloadEmpty ? "\n" : "\n\n") + (!isPayloadEmpty ? (isMultiformData ? payLoad : this.formatJson(payLoad)) : ''))
        }
        return (data?.firstLine ? data?.firstLine + "\n" : "") + (data?.json && Object.keys(data?.json).length > 0 ? this.formatJson(data.json) : "");
      }  
      
}

export default transform