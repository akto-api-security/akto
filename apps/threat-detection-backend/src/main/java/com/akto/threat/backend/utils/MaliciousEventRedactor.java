package com.akto.threat.backend.utils;

import com.akto.dao.context.Context;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redacts sensitive fields (whatever this account has marked "redacted" under Data Types -
 * password, email, custom types, etc.) out of a captured malicious-event payload before it is
 * queued for persistence, so the raw value never reaches Mongo or crosses the network to the
 * dashboard. Reuses the same classification engine (KeyTypes/CustomDataType/AktoDataType) as
 * general API traffic sample redaction, so *what* gets flagged stays consistent with the rest of
 * the product. Unlike that generic engine's flat "****", each match here is replaced with a
 * freshly generated random value shaped like its type (a new fake email/card/etc. every time) -
 * nothing readable, and no single fixed placeholder that would itself become a recognizable
 * fingerprint across every redacted record.
 */
public class MaliciousEventRedactor {

    private static final LoggerMaker logger = new LoggerMaker(MaliciousEventRedactor.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    // rawPayload is the {"requestPayload": "<json>", "responsePayload": "<json>", ...} envelope
    // string used throughout this feature (see ThreatUtils#repairConfigScanEnvelope, and the
    // dashboard's parseAktoPayload/_parseAktoOuter) - requestPayload/responsePayload are
    // themselves JSON-encoded strings, so each is parsed and redacted as its own tree.
    public static String redact(String rawPayload, String accountIdStr) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            return rawPayload;
        }

        int accountId;
        try {
            accountId = Integer.parseInt(accountIdStr);
        } catch (NumberFormatException e) {
            return rawPayload;
        }

        Context.accountId.set(accountId);
        try {
            JsonNode outer = mapper.readTree(rawPayload);
            if (outer == null || !outer.isObject()) {
                return rawPayload;
            }
            ObjectNode outerObj = (ObjectNode) outer;
            redactNestedField(outerObj, "requestPayload");
            redactNestedField(outerObj, "responsePayload");
            return outerObj.toString();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error redacting malicious event payload for account " + accountIdStr);
            return rawPayload;
        } finally {
            Context.resetContextThreadLocals();
        }
    }

    private static void redactNestedField(ObjectNode outer, String field) {
        JsonNode fieldNode = outer.get(field);
        if (fieldNode == null || !fieldNode.isTextual()) {
            return;
        }
        String nestedStr = fieldNode.asText();
        if (nestedStr == null || nestedStr.isEmpty()) {
            return;
        }
        try {
            JsonNode nestedTree = mapper.readTree(nestedStr);
            if (nestedTree == null) {
                return;
            }
            redactTree(null, nestedTree);
            outer.put(field, nestedTree.toString());
        } catch (Exception e) {
            // Not a JSON-shaped payload (e.g. raw text/XML capture) - leave the field untouched.
        }
    }

    // Same tree walk as RedactParser.change(), except the substituted value is generated fresh
    // per match (via randomValueForSubType) instead of one fixed string for every field.
    private static void redactTree(String parentName, JsonNode parent) {
        if (parent == null) {
            return;
        }

        if (parent.isArray()) {
            ArrayNode arrayNode = (ArrayNode) parent;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode arrayElement = arrayNode.get(i);
                if (arrayElement.isValueNode()) {
                    SingleTypeInfo.SubType subType = KeyTypes.findSubType(arrayElement.asText(), parentName, null);
                    if (SingleTypeInfo.isRedacted(subType.getName())) {
                        arrayNode.set(i, new TextNode(randomValueForSubType(subType.getName())));
                    }
                } else {
                    redactTree(parentName, arrayElement);
                }
            }
        } else {
            Iterator<String> fieldNames = parent.fieldNames();
            while (fieldNames.hasNext()) {
                String f = fieldNames.next();
                JsonNode fieldValue = parent.get(f);
                if (fieldValue.isValueNode()) {
                    SingleTypeInfo.SubType subType = KeyTypes.findSubType(fieldValue.asText(), f, null);
                    if (SingleTypeInfo.isRedacted(subType.getName())) {
                        ((ObjectNode) parent).put(f, randomValueForSubType(subType.getName()));
                    }
                } else {
                    redactTree(f, fieldValue);
                }
            }
        }
    }

    // A fresh, realistic-shaped but entirely fake value each call - never the same value twice,
    // never anything derived from the real one. Falls back to a plain random alphanumeric string
    // for custom account types (e.g. "PASSWORD", "EMPLOYEE_ID") that carry no inherent shape.
    private static String randomValueForSubType(String subTypeName) {
        if (subTypeName == null) {
            return randomAlphanumeric(12);
        }
        switch (subTypeName) {
            case "EMAIL":
                return randomAlphanumeric(8).toLowerCase() + "@" + randomAlphanumeric(6).toLowerCase() + ".com";
            case "CREDIT_CARD":
                return randomDigits(4) + "-" + randomDigits(4) + "-" + randomDigits(4) + "-" + randomDigits(4);
            case "SSN":
                return randomDigits(3) + "-" + randomDigits(2) + "-" + randomDigits(4);
            case "PHONE_NUMBER":
                return randomDigits(3) + "-" + randomDigits(3) + "-" + randomDigits(4);
            case "IP_ADDRESS":
                return randomInt(256) + "." + randomInt(256) + "." + randomInt(256) + "." + randomInt(256);
            case "UUID":
                return UUID.randomUUID().toString();
            case "JWT":
                return randomAlphanumeric(12) + "." + randomAlphanumeric(20) + "." + randomAlphanumeric(16);
            case "URL":
                return "https://" + randomAlphanumeric(8).toLowerCase() + ".com/" + randomAlphanumeric(6).toLowerCase();
            case "VIN":
                return randomAlphanumeric(17).toUpperCase();
            default:
                return randomAlphanumeric(12);
        }
    }

    private static String randomAlphanumeric(int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    private static String randomDigits(int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private static int randomInt(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }
}
