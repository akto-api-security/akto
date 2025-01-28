package com.akto.test_editor.filter.data_operands_impl;

import java.util.List;

import com.akto.dao.context.Context;
import com.akto.dto.CustomDataType;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.SingleTypeInfo;

public class DatatypeFilter extends DataOperandsImpl {

    @Override
    public Boolean isValid(DataOperandFilterRequest dataOperandFilterRequest) {
        Object data = dataOperandFilterRequest.getData();
        Object querySet = dataOperandFilterRequest.getQueryset();        
        try {
            List<String> queryList = (List) querySet;
            if (queryList == null || queryList.size() == 0) {
                return false;
            }

            if (data instanceof String && queryList.get(0).equalsIgnoreCase("string")) {
                return true;
            }
            if (data instanceof Integer && queryList.get(0).equalsIgnoreCase("number")) {
                return true;
            }
            if (data instanceof Boolean && queryList.get(0).equalsIgnoreCase("boolean")) {
                return true;
            }

            int accountId = Context.accountId.get();
            String typeToCheck = queryList.get(0);
            String[] typeArr = typeToCheck.split("\\.");
            String dataType = typeArr.length > 0 ? typeArr[0] : "";
            dataType = dataType.replace("_", " ");
            String position = typeArr.length > 1 ? typeArr[1] : "";
            if (SingleTypeInfo.getCustomDataTypeMap(accountId).containsKey(dataType)) {
                CustomDataType temp = SingleTypeInfo.getCustomDataTypeMap(accountId).get(dataType);
                boolean isValid = false;
                switch (position) {
                    case "key":
                        Conditions conditions = temp.getValueConditions();
                        temp.setValueConditions(null);
                        isValid = temp.validate(null, data);
                        temp.setValueConditions(conditions);
                        break;
                    case "value":
                        conditions = temp.getKeyConditions();
                        temp.setKeyConditions(null);
                        isValid = temp.validate(data, null);
                        temp.setKeyConditions(conditions);
                        break;
                    default:
                        break;
                }
                return isValid;
            }

            dataType = dataType.replace(" ", "_");

            if (SingleTypeInfo.getAktoDataTypeMap(accountId).containsKey(dataType)) {
                boolean isValid = false;
                SingleTypeInfo.SubType subType = KeyTypes.findSubType(data, null, null, true);
                isValid = subType.getName().equals(dataType);
                return isValid;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
        
    }

}
