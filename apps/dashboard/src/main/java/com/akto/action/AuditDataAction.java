package com.akto.action;

import com.akto.dao.McpAuditInfoDao;
import com.akto.dto.McpAuditInfo;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class AuditDataAction implements Action {
    @Setter
    private List<McpAuditInfo> auditData;

    private String lastDetected;
    private String type;

    @Getter
    private String updatedTs;
    @Getter
    private String markedBy;
    @Getter
    private String description;


    public String fetchAuditData() {
        auditData = McpAuditInfoDao.instance.findMarkedByEmptySortedByLastDetected(1, 20);
        return SUCCESS.toUpperCase();
    }

    public String insertAuditData() {
        McpAuditInfoDao.instance.insertAuditInfoWithMarkedByAndDescription(markedBy, description);

        return SUCCESS.toUpperCase();
    }

    @Getter @Setter
    private String _id;

    public String updateAuditData() {
        McpAuditInfoDao.instance.updateAuditInfo(_id, markedBy, description);
        return SUCCESS.toUpperCase();
    }

    public List<McpAuditInfo> getAuditData() {
        return auditData;
    }

    public void setMarkedBy(String markedBy) {
        this.markedBy = markedBy;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setLastDetected(String lastDetected) {
        this.lastDetected = lastDetected;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String execute() throws Exception {
        return "";
    }
}
