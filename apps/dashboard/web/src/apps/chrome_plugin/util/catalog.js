
let SubType = {
    NULL: "NULL",
    INTEGER: "INTEGER",
    FLOAT: "FLOAT",
    BOOL: "BOOL",
    STRING: "STRING",
    OTHER: "OTHER"
}

function isInt(n){
    return Number(n) === n && n % 1 === 0;
}

function isFloat(n){
    return Number(n) === n && n % 1 !== 0;
}

function patterns() {
    let ret = {}
    ret["EMAIL"] = new RegExp ("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");
    ret["URL"] = new RegExp ("^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:|:blank:]])?$");
    ret["CREDIT_CARD"] = new RegExp ("^((4\\d{3})|(5[1-5]\\d{2})|(6011)|(7\\d{3}))-?\\d{4}-?\\d{4}-?\\d{4}|3[4,7]\\d{13}$");
    ret["SSN"] = new RegExp ("^\\d{3}-\\d{2}-\\d{4}$");
    ret["UUID"] = new RegExp ("^[A-Z0-9]{8}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{12}$");
    ret["PAN_CARD"] = new RegExp ("^[A-Z]{5}[0-9]{4}[A-Z]{1}$");
    ret["PHONE_NUMBER_US"] = new RegExp ("^\D?(\d{3})\D?\D?(\d{3})\D?(\d{4})$");
    ret["PHONE_NUMBER_INDIA"] = /^(?:\s+|)((0|(?:(\+|)91))(?:\s|-)*(?:(?:\d(?:\s|-)*\d{9})|(?:\d{2}(?:\s|-)*\d{8})|(?:\d{3}(?:\s|-)*\d{7})|\d{10}))(?:\s+|)$/
    return ret
}

function findSubType(o) {

    let patternMap = patterns()
    for(var key in patternMap) {
        let value = patternMap[key]
        let res = value.exec(o+"")
        if (res) {
            return key
        }
    }

    if (+o) {
        o = +o
    }

    if (o === null) {
        return SubType.NULL;
    } 

    if (isInt(o)) {
        return SubType.INTEGER
    }

    if (isFloat(o)) {
        return SubType.FLOAT
    }

    if (typeof o == "boolean") {
        return SubType.BOOL
    }

    if (typeof o === "string") {
        return SubType.STRING;
    }

    return SubType.OTHER;    
}

function createSingleTypeInfo(subType) {
    return {
        type: subType,
        values: []
    }
}

function flattenHelper(obj, result, prefix) {
    if (!obj || prefix.length > 100) {
        return 
    }

    if (Array.isArray(obj)) {
        for(var index in obj) {
            flattenHelper(obj[index], result, prefix)
        }
    } else if (typeof obj === 'object') {
        Object.keys(obj).forEach(key => {
            flattenHelper(obj[key], result, prefix+"."+key)
        })
    } else {
        let info = result[prefix]
        if (!info) {
            info = {}
            result[prefix] = info
        }
        
        let subType = findSubType(obj)

        if(!subType) {
            subType = SubType.OTHER
        } 
        
        let subTypeInfo = info[subType]

        if (!subTypeInfo) {
            subTypeInfo = createSingleTypeInfo(subType)
            info[subType] = subTypeInfo
        }

        subTypeInfo.values.push(obj)
    }
}

function flatten(details, prefix) {
    let ret = {}
    if (!details) {
        return ret
    }
    flattenHelper(details, ret, prefix)
    return ret
}

function tryJson(str) {
    try {
        return JSON.parse(str);
    } catch (e) {
        return null;
    }
}

function getQueryParams(qs) {
    qs = qs.split('+').join(' ');

    var params = {},
        tokens,
        re = /[?&]?([^=]+)=([^&]*)/g;

    while (tokens = re.exec(qs)) {
        params[decodeURIComponent(tokens[1])] = decodeURIComponent(tokens[2]);
    }

    return params;
}

export default {
    tryParamsOrJson: obj => {
        try {
            if (obj == null) {
                return {}
            }

            if (typeof obj=== "object") {
                return flatten(obj, "")
            }

            if (typeof obj === "string") {
                let json = tryJson(obj, "")

                if (json) {
                    return flatten(json, "")
                } else {
                    return flatten(getQueryParams(obj), "")
                }
            }
        } catch (e) {
            return null;
        }

        return {}
    },
    
    toSuperType: subType => {
        return (patterns()[subType]) ? "STRING" : subType
    },

    isSensitive: str => {
        return !!(patterns()[str])
    }  
}