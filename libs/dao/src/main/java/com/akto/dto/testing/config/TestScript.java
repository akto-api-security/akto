package com.akto.dto.testing.config;
import java.util.Objects;

import org.bson.codecs.pojo.annotations.BsonId;

import com.akto.dao.context.Context;

public class TestScript {


    public enum Type {
        PRE_TEST, PRE_REQUEST, POST_REQUEST, POST_TEST
    }

    public static final String ID = "_id";
    @BsonId
    private String id;

    public static final String JAVASCRIPT = "javascript";
    private String javascript;
    
    private Type type;

    public static final String AUTHOR = "author";
    private String author;

    private int lastCreatedAt;

    public static final String LAST_UPDATED_AT = "lastUpdatedAt";
    private int lastUpdatedAt;
    

    public TestScript() {
    }

    public TestScript(String id, String javascript, Type type, String author, int lastUpdatedAt) {
        this.id = id;
        this.javascript = javascript;
        this.type = type;
        this.author = author;
        this.lastCreatedAt = Context.now();
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public String getJavascript() {
        return this.javascript;
    }

    public void setJavascript(String javascript) {
        this.javascript = javascript;
    }

    public Type getType() {
        return this.type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getAuthor() {
        return this.author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getLastCreatedAt() {
        return this.lastCreatedAt;
    }

    public void setLastCreatedAt(int lastCreatedAt) {
        this.lastCreatedAt = lastCreatedAt;
    }

    public int getLastUpdatedAt() {
        return this.lastUpdatedAt;
    }

    public void setLastUpdatedAt(int lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof TestScript)) {
            return false;
        }
        TestScript testScript = (TestScript) o;
        return Objects.equals(javascript, testScript.javascript) && Objects.equals(type, testScript.type) && Objects.equals(author, testScript.author) && lastCreatedAt == testScript.lastCreatedAt && lastUpdatedAt == testScript.lastUpdatedAt;
    }

    @Override
    public int hashCode() {
        return Objects.hash(javascript, type, author, lastCreatedAt, lastUpdatedAt);
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", javascript='" + getJavascript() + "'" +
            ", type='" + getType() + "'" +
            ", author='" + getAuthor() + "'" +
            ", lastCreatedAt='" + getLastCreatedAt() + "'" +
            ", lastUpdatedAt='" + getLastUpdatedAt() + "'" +
            "}";
    }

    
}
