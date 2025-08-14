package com.akto.proto.generated.threat_detection.message.http_response_param.v1;

import java.util.List;
import java.util.ArrayList;

public final class StringList {
    private List<String> values = new ArrayList<>();

    private StringList() {}

    public static StringList getDefaultInstance() {
        return new StringList();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private StringList result = new StringList();

        public Builder addValues(String value) {
            result.values.add(value);
            return this;
        }

        public Builder addAllValues(List<String> values) {
            result.values.addAll(values);
            return this;
        }

        public StringList build() {
            return result;
        }
    }

    public List<String> getValuesList() {
        return new ArrayList<>(values);
    }

    public int getValuesCount() {
        return values.size();
    }

    public String getValues(int index) {
        return values.get(index);
    }
}
