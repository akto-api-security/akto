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
            sensitivePositions: sensitivePosition
        }

        return resultObj;
    },

    convertDataToState: function(sensitiveAlways, sensitivePosition){
        if(sensitiveAlways){return '3'}
        else if(sensitivePosition.length === 0){return '4'}
        else if(sensitivePosition.includes("REQUEST_PAYLOAD")){return '1'}
        else{ return '2'}
    },

}

export default func