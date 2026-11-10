package com.akto.dto.testing.config;
import java.util.Objects;

import org.bson.codecs.pojo.annotations.BsonId;

import com.akto.dao.context.Context;

public class TestScript {


    public enum Type {
        PRE_TEST, PRE_REQUEST, POST_REQUEST, POST_TEST
    }

    // Value of TestingRunConfig.preRequestScriptId / postRequestScriptId meaning "explicitly no script".
    // A null id means "use the default-test-{type} script"; see ScriptSelection.
    public static final String NO_SCRIPT = "";

    public static final String DEFAULT_PRE_NAME = "default-test-pre";
    public static final String DEFAULT_POST_NAME = "default-test-post";

    public static final String ID = "_id";
    @BsonId
    private String id;

    public static final String NAME = "name";
    private String name;

    public static final String TYPE = "type";
    public static final String JAVASCRIPT = "javascript";
    private String javascript;
    
    private Type type;

    // Legacy single-author field, kept so pre-library documents still decode. New code uses createdBy / lastUpdatedBy.
    public static final String AUTHOR = "author";
    private String author;

    public static final String CREATED_BY = "createdBy";
    private String createdBy;

    public static final String LAST_UPDATED_BY = "lastUpdatedBy";
    private String lastUpdatedBy;

    public static final String LAST_CREATED_AT = "lastCreatedAt";
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

    public TestScript(String id, String name, String javascript, Type type, String createdBy, String lastUpdatedBy,
            int lastCreatedAt, int lastUpdatedAt) {
        this.id = id;
        this.name = name;
        this.javascript = javascript;
        this.type = type;
        this.author = lastUpdatedBy;
        this.createdBy = createdBy;
        this.lastUpdatedBy = lastUpdatedBy;
        this.lastCreatedAt = lastCreatedAt;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    /** Name of the script that stands in for the pre-library one-script-per-type model. */
    public static String defaultNameFor(Type type) {
        if (type == Type.PRE_REQUEST) {
            return DEFAULT_PRE_NAME;
        }
        if (type == Type.POST_REQUEST) {
            return DEFAULT_POST_NAME;
        }
        throw new IllegalArgumentException("No default script for type " + type);
    }

    /**
     * The type an untyped script request means. Mini-testing builds from before post-request scripts
     * (pre 2026-03-15) call the database-abstractor's fetchTestScript with no type and run the result
     * as their pre-request script, so no type means PRE_REQUEST.
     */
    public static Type typeForUntypedRequest(Type requested) {
        return requested != null ? requested : Type.PRE_REQUEST;
    }

    public static boolean isDefaultName(String name) {
        return DEFAULT_PRE_NAME.equals(name) || DEFAULT_POST_NAME.equals(name);
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getLastUpdatedBy() {
        return this.lastUpdatedBy;
    }

    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
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
        return Objects.equals(name, testScript.name) && Objects.equals(javascript, testScript.javascript) && Objects.equals(type, testScript.type) && Objects.equals(author, testScript.author) && Objects.equals(createdBy, testScript.createdBy) && Objects.equals(lastUpdatedBy, testScript.lastUpdatedBy) && lastCreatedAt == testScript.lastCreatedAt && lastUpdatedAt == testScript.lastUpdatedAt;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, javascript, type, author, createdBy, lastUpdatedBy, lastCreatedAt, lastUpdatedAt);
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", name='" + getName() + "'" +
            ", javascript='" + getJavascript() + "'" +
            ", type='" + getType() + "'" +
            ", author='" + getAuthor() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", lastUpdatedBy='" + getLastUpdatedBy() + "'" +
            ", lastCreatedAt='" + getLastCreatedAt() + "'" +
            ", lastUpdatedAt='" + getLastUpdatedAt() + "'" +
            "}";
    }

    
}
