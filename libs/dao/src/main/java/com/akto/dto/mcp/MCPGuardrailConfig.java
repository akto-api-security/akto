package com.akto.dto.mcp;

import com.akto.dto.test_editor.ConfigParserResult;
import com.akto.dto.test_editor.ExecutorConfigParserResult;
import com.akto.dto.test_editor.Info;

public class MCPGuardrailConfig {
    private String id;
    public static final String ID = "id";
    private ConfigParserResult filter;
    public static final String FILTER = "filter";
    public static final String CREATED_AT = "createdAt";
    private int createdAt;
    public static final String UPDATED_AT = "updatedAt";
    private int updatedAt;
    public static final String _AUTHOR = "author";
    private String author;
    public static final String _CONTENT = "content";
    private String content;
    public static final String _INFO = "info";
    private Info info;
    private ExecutorConfigParserResult executor;

    public MCPGuardrailConfig(String id, ConfigParserResult filter) {
        this.id = id;
        this.filter = filter;
    }

    public MCPGuardrailConfig() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ConfigParserResult getFilter() {
        return filter;
    }

    public void setFilter(ConfigParserResult filter) {
        this.filter = filter;
    }

    public int getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(int createdAt) {
        this.createdAt = createdAt;
    }

    public int getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(int updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public ExecutorConfigParserResult getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorConfigParserResult executor) {
        this.executor = executor;
    }


    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
    }
}
