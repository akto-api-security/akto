package com.akto.dto.runtime_filters;

import com.akto.dto.CustomFilter;
import com.akto.dto.HttpResponseParams;

public class ResponseCodeRuntimeFilter extends CustomFilter {

    private int startValue;
    private int endValue;

    public ResponseCodeRuntimeFilter() {
        super();
    }

    public ResponseCodeRuntimeFilter( int startValue, int endValue) {
        super();
        this.startValue = startValue;
        this.endValue = endValue;
    }

    @Override
    public boolean process(HttpResponseParams httpResponseParams) {
        int responseCode = httpResponseParams.getStatusCode();
        return responseCode >= startValue && responseCode <= endValue;
    }

    public int getStartValue() {
        return startValue;
    }

    public void setStartValue(int startValue) {
        this.startValue = startValue;
    }

    public int getEndValue() {
        return endValue;
    }

    public void setEndValue(int endValue) {
        this.endValue = endValue;
    }
}
