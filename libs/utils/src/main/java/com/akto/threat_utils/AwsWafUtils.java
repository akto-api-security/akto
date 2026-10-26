package com.akto.threat_utils;

import com.akto.dto.Config;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.wafv2.Wafv2Client;
import software.amazon.awssdk.services.wafv2.model.CreateIpSetRequest;
import software.amazon.awssdk.services.wafv2.model.GetIpSetRequest;
import software.amazon.awssdk.services.wafv2.model.GetIpSetResponse;
import software.amazon.awssdk.services.wafv2.model.IPAddressVersion;
import software.amazon.awssdk.services.wafv2.model.IPSetSummary;
import software.amazon.awssdk.services.wafv2.model.ListIpSetsRequest;
import software.amazon.awssdk.services.wafv2.model.ListIpSetsResponse;
import software.amazon.awssdk.services.wafv2.model.UpdateIpSetRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AwsWafUtils {

    private static final String SCOPE = "REGIONAL";
    private static final LoggerMaker loggerMaker = new LoggerMaker(AwsWafUtils.class, LogDb.THREAT_DETECTION);

    public static Wafv2Client fetchAwsWafClient(String accessKey, String secretKey, String region) {
        return Wafv2Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            ))
            .build();
    }

    public static IPSetSummary getOrCreateIpSet(Wafv2Client wafv2Client, String ipSetName) {
        String nextMarker = null;
        do {
            ListIpSetsResponse listResponse = wafv2Client.listIPSets(ListIpSetsRequest.builder()
                    .scope(SCOPE)
                    .limit(100)
                    .nextMarker(nextMarker)
                    .build());
            for (IPSetSummary ipSet : listResponse.ipSets()) {
                if (ipSetName.equals(ipSet.name())) {
                    return ipSet;
                }
            }
            nextMarker = listResponse.ipSets().isEmpty() ? null : listResponse.nextMarker();
        } while (nextMarker != null);

        CreateIpSetRequest createRequest = CreateIpSetRequest.builder()
                .name(ipSetName)
                .scope(SCOPE)
                .ipAddressVersion(IPAddressVersion.IPV4)
                .addresses(Collections.emptyList())
                .build();
        return wafv2Client.createIPSet(createRequest).summary();
    }

    public static boolean blockActorIps(Config.AwsWafConfig config, List<String> ips) {
        return updateIpSet(config, ips, true);
    }

    public static boolean unblockActorIps(Config.AwsWafConfig config, List<String> ips) {
        return updateIpSet(config, ips, false);
    }

    private static boolean updateIpSet(Config.AwsWafConfig config, List<String> ips, boolean block) {
        if (config == null || ips == null || ips.isEmpty()) {
            loggerMaker.debugAndAddToDb("Invalid parameters for updating AWS WAF ip set");
            return false;
        }

        try {
            Wafv2Client wafClient = fetchAwsWafClient(config.getAwsAccessKey(), config.getAwsSecretKey(), config.getRegion());
            GetIpSetResponse getResponse = wafClient.getIPSet(GetIpSetRequest.builder()
                    .name(config.getRuleSetName())
                    .scope(SCOPE)
                    .id(config.getRuleSetId())
                    .build());

            Set<String> addresses = new LinkedHashSet<>(getResponse.ipSet().addresses());
            boolean changed = false;
            for (String ip : ips) {
                String ipWithCidr = ip + "/32";
                changed |= block ? addresses.add(ipWithCidr) : addresses.remove(ipWithCidr);
            }

            if (changed) {
                wafClient.updateIPSet(UpdateIpSetRequest.builder()
                        .name(config.getRuleSetName())
                        .scope(SCOPE)
                        .id(config.getRuleSetId())
                        .addresses(new ArrayList<>(addresses))
                        .lockToken(getResponse.lockToken())
                        .build());
            }
            loggerMaker.debugAndAddToDb("AWS WAF ip set updated, block " + block + " for " + ips.size() + " ips");
            return true;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error updating AWS WAF ip set: " + e.getMessage());
            return false;
        }
    }
}
