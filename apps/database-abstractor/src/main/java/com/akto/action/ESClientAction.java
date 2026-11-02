package com.akto.action;

import java.util.List;

import com.akto.dao.context.Context;
import com.akto.utils.KafkaUtils;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.elasticsearch.ElasticSearchClient;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;


public class ESClientAction {

    KafkaUtils kafkaUtils = new KafkaUtils();

    @Getter @Setter
    private List<com.akto.utils.elasticsearch.AgentQueryRecord> agentQueryRecords;

    public String storeAgentQueryRecords() {
        int accId = Context.accountId.get();
        if (kafkaUtils.isWriteEnabled()) {
            kafkaUtils.insertDataSecondary(agentQueryRecords, "storeAgentQueryRecords", accId);
        } else {
            try {
                ElasticSearchClient.instance().bulkIndexAgentQueryRecords(agentQueryRecords);
            } catch (Exception e) {
                return Action.ERROR.toUpperCase();
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    @Setter
    private String messageId;
    @Getter
    private List<AgentQueryRecord> recordsForTraceId;
    public String getAgentTracesForMessageId(){
        if (messageId == null || messageId.isEmpty()) {
            return Action.ERROR.toUpperCase();
        }
        int accId = Context.accountId.get();
        this.recordsForTraceId = ElasticSearchClient.instance().scrollQueryData(accId, messageId);
        return Action.SUCCESS.toUpperCase();
    }
    
}
