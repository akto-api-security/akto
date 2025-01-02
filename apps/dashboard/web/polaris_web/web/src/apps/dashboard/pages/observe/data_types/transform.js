import globalFunc from "@/util/func"
const func = {
    initialObj: {
        id:"",
        active: 'false',
        name: "",
        valueConditions: { predicates: [], operator: "OR" },
        keyConditions: { predicates: [], operator: "OR" },
        sensitiveState: '4',
        operator: "OR",
        dataType: "Custom",
        redacted: 'false',
        priority: 'CRITICAL',
        categoriesList: [],
        iconString: ""
      },

    convertToSensitiveData: function(state) {
        let sensitiveAlways = false;
        let sensitivePosition = [] ;

        switch (state){
            case '1':
                sensitivePosition = ["REQUEST_PAYLOAD", "REQUEST_HEADER"];
                break;

            case '2':
                sensitivePosition = ["RESPONSE_PAYLOAD", "RESPONSE_HEADER"];
                break;

            case '3':
                sensitiveAlways= true;
                break;

            default:
                    break;
        }
        let resultObj= {
            sensitiveAlways: sensitiveAlways,
            sensitivePosition: sensitivePosition,
        }

        return resultObj;
    },

    convertDataToState: function(sensitiveAlways, sensitivePosition){
        if(sensitiveAlways){return '3'}
        else if(sensitivePosition.length === 0){return '4'}
        else if(sensitivePosition.includes("REQUEST_PAYLOAD")){return '1'}
        else{ return '2'}
    },
    
    fillInitialState: function({dataObj, type}){
        let initialObj = {...func.initialObj};
        initialObj.id = dataObj.id;
        initialObj.name = dataObj.name
        initialObj.dataType = type
        initialObj.redacted = dataObj.redacted.toString()
        initialObj.priority = dataObj?.dataTypePriority || "CRITICAL"
        initialObj.categoriesList = dataObj?.categoriesList || []
        initialObj.iconString = dataObj?.iconString || globalFunc.getSensitiveIcons(dataObj.name)
        let state = func.convertDataToState(dataObj.sensitiveAlways, dataObj.sensitivePosition)
        initialObj.sensitiveState = state
        initialObj.active = dataObj.active.toString()
        if(type === 'Custom'){
          initialObj.operator= dataObj.operator
          initialObj.creatorId= dataObj.creatorId
          initialObj.skipDataTypeTestTemplateMapping = dataObj.skipDataTypeTestTemplateMapping
        }
        if(dataObj.keyConditions){
          initialObj.keyConditions = dataObj.keyConditions
        }
        if(dataObj.valueConditions){
          initialObj.valueConditions = dataObj.valueConditions
        }
        return initialObj;
    },

    convertMapFunction :(element)=> {
        return{
            type: element.type,
            valueMap:{
                value: element.value
            }
        }
    },

    convertDataForCustomPayload : function(state){

        const keyArr = state.keyConditions.predicates.map(this.convertMapFunction)
        const valueArr = state.valueConditions.predicates.map(this.convertMapFunction)

        let sensitiveObj = this.convertToSensitiveData(state.sensitiveState)

        let finalObj = {
            active: JSON.parse(state.active),
            createNew: state.id ? false : true,
            id: state.id,
            keyConditionFromUsers: keyArr,
            keyOperator: state.keyConditions.operator,
            name: state.name,
            operator: state.operator,
            sensitiveAlways: sensitiveObj.sensitiveAlways,
            sensitivePosition: sensitiveObj.sensitivePosition,
            valueConditionFromUsers: valueArr,
            valueOperator: state.valueConditions.operator,
            redacted: state.redacted,
            skipDataTypeTestTemplateMapping: state.skipDataTypeTestTemplateMapping,
            iconString: state?.iconString?.displayName,
            categoriesList: state?.categoriesList,
            dataTypePriority: state?.priority ? state.priority.toUpperCase() : ""
        }

        return finalObj
    },

    getRegexObj: function(regexObj){
        const obj = {
            ...this.initialObj,
            name: regexObj.name,
            valueConditions: regexObj.valueConditions,
            active: true,
            sensitiveState: '2',
            priority: regexObj?.dataTypePriority || "",
            categoriesList: regexObj?.categoriesList || [],
            iconString: regexObj?.iconString || globalFunc.getSensitiveIcons(regexObj.name)
        }

        return obj
    }
}

export default func