const func = {
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

    convertDataForCustomPayload : function(keyConditions,keyOp,valueConditions,valueOp,operator,typeName,sensitiveState,status,id){

        const keyArr = keyConditions.map((element)=> {
            return{
                type: element.type,
                valueMap:{
                    value: element.value
                }
            }
        })

        const valueArr = valueConditions.map((element)=> {
            return{
                type: element.type,
                valueMap:{
                    value: element.value
                }
            }
        })

        let sensitiveObj = this.convertToSensitiveData(sensitiveState)

        let finalObj = {
            active: JSON.parse(status),
            createNew: id ? false : true,
            id: id,
            keyConditionFromUsers: keyArr,
            keyOperator: keyOp,
            name: typeName,
            operator: operator,
            sensitiveAlways: sensitiveObj.sensitiveAlways,
            sensitivePosition: sensitiveObj.sensitivePosition,
            valueConditionFromUsers: valueArr,
            valueOperator: valueOp,
        }

        return finalObj
    },
}

export default func