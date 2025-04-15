package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dto.test_editor.DataOperandFilterRequest;

public abstract class DataOperandsImpl {
    
    public static Boolean result;
    public static Object data;
    public String validationReson;
    public static Integer dataInt;
    public static Boolean dataBool;
    public static  Boolean queryBool;
    public static Object query;
    public static String dataStr;
    public static String validationString;

    public abstract ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest);
    
}
