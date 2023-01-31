package com.akto.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dto.type.SingleTypeInfo.ParamId;

public class IgnoreData {
	Map<String, List<ParamId>> ignoredKeysInSelectedAPIs;
	Set<String> ignoredKeysInAllAPIs;
    public IgnoreData() {
    }
    public IgnoreData(Map<String, List<ParamId>> ignoredKeysInSelectedAPIs, Set<String> ignoredKeysInAllAPIs) {
        this.ignoredKeysInSelectedAPIs = ignoredKeysInSelectedAPIs;
        this.ignoredKeysInAllAPIs = ignoredKeysInAllAPIs;
    }
    public Map<String, List<ParamId>> getIgnoredKeysInSelectedAPIs() {
        return ignoredKeysInSelectedAPIs;
    }
    public void setIgnoredKeysInSelectedAPIs(Map<String, List<ParamId>> ignoredKeysInSelectedAPIs) {
        this.ignoredKeysInSelectedAPIs = ignoredKeysInSelectedAPIs;
    }
    public Set<String> getIgnoredKeysInAllAPIs() {
        return ignoredKeysInAllAPIs;
    }
    public void setIgnoredKeysInAllAPIs(Set<String> ignoredKeysInAllAPIs) {
        this.ignoredKeysInAllAPIs = ignoredKeysInAllAPIs;
    }
}
