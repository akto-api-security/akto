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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AwsWafUtils {

    private static final String SCOPE = "REGIONAL";
    private static final int MAX_IP_SET_ADDRESSES = 10000;
    private static final LoggerMaker loggerMaker = new LoggerMaker(AwsWafUtils.class, LogDb.THREAT_DETECTION);
    private static final Pattern IPV4 = Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    // The akto-blocked-ips set is IPv4, and one non-IPv4 entry makes AWS reject the whole update
    public static List<String> ipv4Only(List<String> ips) {
        List<String> ipv4 = ips.stream().filter(ip -> ip != null && IPV4.matcher(ip).matches()).collect(Collectors.toList());
        if (ipv4.size() < ips.size()) {
            loggerMaker.infoAndAddToDb("Skipping " + (ips.size() - ipv4.size()) + " non-IPv4 actors for AWS WAF");
        }
        return ipv4;
    }

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

    // Returns the ips that are in the ip set after the update, or null on failure
    public static List<String> blockActorIps(Config.AwsWafConfig config, List<String> ips) {
        return updateIpSet(config, ips, true);
    }

    public static boolean unblockActorIps(Config.AwsWafConfig config, List<String> ips) {
        return updateIpSet(config, ips, false) != null;
    }

    private static List<String> updateIpSet(Config.AwsWafConfig config, List<String> ips, boolean block) {
        if (config == null || ips == null || ips.isEmpty()) {
            loggerMaker.debugAndAddToDb("Invalid parameters for updating AWS WAF ip set");
            return null;
        }

        try (Wafv2Client wafClient = fetchAwsWafClient(config.getAwsAccessKey(), config.getAwsSecretKey(), config.getRegion())) {
            GetIpSetResponse getResponse = wafClient.getIPSet(GetIpSetRequest.builder()
                    .name(config.getRuleSetName())
                    .scope(SCOPE)
                    .id(config.getRuleSetId())
                    .build());

            Set<String> addresses = new LinkedHashSet<>(getResponse.ipSet().addresses());
            List<String> updatedIps = new ArrayList<>();
            int skipped = 0;
            boolean changed = false;
            for (String ip : ips) {
                String ipWithCidr = ip + "/32";
                if (!block) {
                    changed |= addresses.remove(ipWithCidr);
                    updatedIps.add(ip);
                } else if (addresses.contains(ipWithCidr)) {
                    updatedIps.add(ip);
                } else if (addresses.size() < MAX_IP_SET_ADDRESSES) {
                    addresses.add(ipWithCidr);
                    updatedIps.add(ip);
                    changed = true;
                } else {
                    skipped++;
                }
            }

            if (skipped > 0) {
                loggerMaker.errorAndAddToDb("AWS WAF ip set " + config.getRuleSetName() + " is full (" + MAX_IP_SET_ADDRESSES + " addresses), skipped " + skipped + " ips");
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
            loggerMaker.debugAndAddToDb("AWS WAF ip set updated, block " + block + " for " + updatedIps.size() + " ips");
            return updatedIps;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error updating AWS WAF ip set: " + e.getMessage());
            return null;
        }
    }
}
