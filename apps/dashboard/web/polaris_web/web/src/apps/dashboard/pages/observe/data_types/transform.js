const func = {
    initialObj: {
        id:"",
        active: 'false',
        name: "",
        valueConditions: { predicates: [], operator: "OR" },
        keyConditions: { predicates: [], operator: "OR" },
        sensitiveState: '4',
        operator: "OR",
        dataType: "Custom"
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
            sensitivePosition: sensitivePosition
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
        let state = func.convertDataToState(dataObj.sensitiveAlways, dataObj.sensitivePosition)
        initialObj.sensitiveState = state
        if(type === 'Custom'){
          initialObj.active = dataObj.active.toString()
          initialObj.operator= dataObj.operator
        }
        if(dataObj.keyConditions){
          initialObj.keyConditions = dataObj.keyConditions
        }
        if(dataObj.valueConditions){
          initialObj.valueConditions = dataObj.valueConditions
        }
        return initialObj;
    },

    convertDataForCustomPayload : function(state){

        const keyArr = state.keyConditions.predicates.map((element)=> {
            return{
                type: element.type,
                valueMap:{
                    value: element.value
                }
            }
        })

        const valueArr = state.valueConditions.predicates.map((element)=> {
            return{
                type: element.type,
                valueMap:{
                    value: element.value
                }
            }
        })

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
        }

        return finalObj
    },

    getRegexObj: function(regexObj){
        const obj = {
            ...this.initialObj,
            name: regexObj.name,
            valueConditions: regexObj.valueConditions,
            active: true,
            sensitiveState: '2'
        }

        return obj
    }
}

export default func