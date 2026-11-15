package com.akto.dto.testing;

/**
 * A named text in a smart agent: a knowledge base file, or a custom skill (its name and instructions).
 * A knowledge base file is kept in blob storage (like file inspection content): only its sha256 and size are stored
 * here, and smart testing reads it through cyborg (api/getFileContent). Without blob storage, content is inline.
 */
public class SmartAgentKnowledgeFile {

    private String name;
    private String content;
    private String sha256;
    private int size;

    public SmartAgentKnowledgeFile() { }

    public SmartAgentKnowledgeFile(String name, String content) {
        this.name = name;
        this.content = content;
    }

    public SmartAgentKnowledgeFile(String name, String content, String sha256, int size) {
        this.name = name;
        this.content = content;
        this.sha256 = sha256;
        this.size = size;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
