package com.akto.dto.CollectionConditions;

import java.util.Set;

public class ConditionsType {

    private String key;
    private String value;
    private Set<String> urlsList;
    private int position;


    public ConditionsType () {}

    public ConditionsType (String key, String value, Set<String> urlsList) {
        this.key = key;
        this.value = value;
        this.urlsList = urlsList ;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Set<String> getUrlsList() {
        return urlsList;
    }

    public void setUrlsList(Set<String> urlsList) {
        this.urlsList = urlsList;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConditionsType that = (ConditionsType) o;
        return ((key != null && that.key != null && key.equals(that.key)) ||
                (key == null && that.key == null)) &&
                ((value != null && that.value != null && value.equals(that.value)) ||
                        (value == null && that.value == null))
                &&
                ((urlsList != null && that.urlsList != null && urlsList.equals(that.urlsList)) ||
                        (urlsList == null && that.urlsList == null)) &&
                ((position != 0 && that.position != 0 && position == that.position) ||
                        (position == 0 && that.position == 0));
    }

}
