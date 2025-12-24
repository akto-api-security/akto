package com.akto.dto.testing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgentConversation {

    @BsonId
    private String id;
    public static final String ID = "_id";
    private String title;
    private int createdAt;
    private int lastUpdatedAt;
    private int messageCount;

    public AgentConversation(String conversationId) {
        this.id = conversationId;
    }
}
