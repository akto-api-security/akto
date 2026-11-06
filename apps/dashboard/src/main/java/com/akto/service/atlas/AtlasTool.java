package com.akto.service.atlas;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A tool row with its resolved category, status and employees. */
@Getter
@AllArgsConstructor
public final class AtlasTool {

    private final AtlasToolRow row;
    private final String category;
    private final AtlasAssetStatus status;
    // Every employee on the tool's devices, and the ones whose device used it recently.
    private final Set<String> users;
    private final Set<String> recentUsers;

    public String key() {
        return AtlasAssetStatusService.toolKey(row.getRowType(), row.getGroupKey());
    }

    public int devices() {
        return row.getDeviceIds().size();
    }

    public boolean governable() {
        return AtlasAssetStatusService.isGovernable(row.getRowType());
    }

    /** Whether approve and block apply: reviewed tools, and skills flagged malicious. */
    public boolean canDecide() {
        return AtlasAssetStatusService.canDecide(row);
    }

    public boolean needsDecision() {
        return canDecide() && (status == AtlasAssetStatus.UNAPPROVED || status == AtlasAssetStatus.UNDER_REVIEW);
    }

    /** The fields every list and card shows for a tool. */
    public Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("key", key());
        summary.put("groupKey", row.getGroupKey());
        summary.put("name", row.getName());
        summary.put("clientType", row.getClientType());
        summary.put("vendorName", row.getVendorName());
        summary.put("category", category);
        summary.put("status", status.name());
        summary.put("riskScore", row.getRiskScore());
        summary.put("devices", devices());
        summary.put("employees", users.size());
        return summary;
    }

    /** One line on why the tool needs a decision. */
    public String whyLine() {
        List<String> parts = new ArrayList<>();
        if (row.isMalicious()) parts.add("Flagged as malicious");
        if (StringUtils.isNotBlank(row.getRiskReason())) parts.add(row.getRiskReason());
        if (row.isPersonal()) parts.add("Personal accounts");
        if (row.isLocalMcp()) parts.add("Local MCP server");
        if (row.isMisconfigured()) parts.add("Misconfigured on " + row.getMisconfiguredDevices() + " devices");
        parts.add(devices() + (devices() == 1 ? " device" : " devices"));
        return String.join(" · ", parts);
    }
}
