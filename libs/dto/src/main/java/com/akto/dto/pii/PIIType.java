package com.akto.dto.pii;

import java.util.Objects;

import com.akto.dto.type.SingleTypeInfo.SubType;

public class PIIType extends SubType {
    private String name;
    private boolean isSensitive;
    private String regexPattern;
    private boolean onKey;

    public PIIType() {
    }

    public PIIType(String name, boolean isSensitive, String regexPattern, boolean onKey) {
        this.name = name;
        this.isSensitive = isSensitive;
        this.regexPattern = regexPattern;
        this.onKey = onKey;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean getIsSensitive() {
        return this.isSensitive;
    }

    public void setIsSensitive(boolean isSensitive) {
        this.isSensitive = isSensitive;
    }

    public String getRegexPattern() {
        return this.regexPattern;
    }

    public void setRegexPattern(String regexPattern) {
        this.regexPattern = regexPattern;
    }

    public boolean isOnKey() {
        return this.onKey;
    }

    public boolean getOnKey() {
        return this.onKey;
    }

    public void setOnKey(boolean onKey) {
        this.onKey = onKey;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof PIIType)) {
            return false;
        }
        PIIType pIIType = (PIIType) o;
        return Objects.equals(name, pIIType.name) && isSensitive == pIIType.isSensitive && Objects.equals(regexPattern, pIIType.regexPattern) && onKey == pIIType.onKey;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, isSensitive, regexPattern, onKey);
    }

    @Override
    public String toString() {
        return "{" +
            " name='" + getName() + "'" +
            ", isSensitive='" + getIsSensitive() + "'" +
            ", regexPattern='" + getRegexPattern() + "'" +
            ", onKey='" + isOnKey() + "'" +
            "}";
    }

}
