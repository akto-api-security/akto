package com.akto.test_editor.filter.data_operands_impl;

import com.akto.dto.test_editor.DataOperandFilterRequest;

public class ConfidenceFilter extends DataOperandsImpl {
    @Override
    public ValidationResult isValid(DataOperandFilterRequest request) {
        Object valueObj = request.getData();
        Object querySetObj = request.getQueryset();
        double value;
        double threshold;
        try {
            if (valueObj instanceof Double) {
                value = (Double) valueObj;
            } else {
                value = Double.parseDouble(valueObj.toString());
            }
        } catch (Exception e) {
            return new ValidationResult(false, "Value is not a valid fraction");
        }
        if (value < 0.0 || value > 1.0) {
            return new ValidationResult(false, "Value is not in [0.00, 1.00] range");
        }
        try {
            Object thresholdObj = ((java.util.List<?>) querySetObj).get(0);
            if (thresholdObj instanceof Double) {
                threshold = (Double) thresholdObj;
            } else {
                threshold = Double.parseDouble(thresholdObj.toString());
            }
        } catch (Exception e) {
            return new ValidationResult(false, "Threshold is not a valid fraction");
        }
        if (threshold < 0.0 || threshold > 1.0) {
            return new ValidationResult(false, "Threshold is not in [0.00, 1.00] range");
        }
        boolean result = value > threshold;
        return new ValidationResult(result, result ? null : "Value " + value + " is not greater than threshold " + threshold);
    }
}
