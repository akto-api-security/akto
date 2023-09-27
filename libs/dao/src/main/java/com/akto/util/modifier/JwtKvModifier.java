package com.akto.util.modifier;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.google.gson.Gson;

public class JwtKvModifier extends JwtModifier{

    String key, value;

    public JwtKvModifier(String key, String value) {
        super();
        
        this.key = key;
        this.value = value;
    }

    @Override
    public String jwtModify(String jwtKey, String jwtValue) throws Exception {
        String[] jwtArr = jwtValue.split("\\.");
        if (jwtArr.length != 3) return null;
        
        String payload = jwtArr[1];
        String encodedModifiedPayload = payload;
        byte[] decodedPayloadBytes = Base64.getDecoder().decode(payload);
        String decodedPayloadStr = new String(decodedPayloadBytes, StandardCharsets.UTF_8);
        // convert string to map
        Map<String, Object> json = new Gson().fromJson(decodedPayloadStr, Map.class);
        String[] tokensInKey = key.split("\\.");
        Map<String, Object> tempObjToReplace = json;
        for (String token: tokensInKey) {
            Object tempObjToReplaceObj = tempObjToReplace.get(token);
            if (tempObjToReplaceObj != null && tempObjToReplaceObj instanceof Map) {
                tempObjToReplace = (Map) tempObjToReplaceObj;
            }
        }

        Object valueToReplace = value;
        if (NumberUtils.isParsable(value)) {
            if (StringUtils.isNumeric(value)) {
                valueToReplace = Long.parseLong(value);
            } else {
                valueToReplace = Double.parseDouble(value);
            }

        }

        tempObjToReplace.put(tokensInKey[tokensInKey.length - 1], valueToReplace);
        String modifiedPayloadStr = mapper.writeValueAsString(json);

        encodedModifiedPayload = Base64.getEncoder().encodeToString(modifiedPayloadStr.getBytes(StandardCharsets.UTF_8));
        if (encodedModifiedPayload.endsWith("=")) encodedModifiedPayload = encodedModifiedPayload.substring(0, encodedModifiedPayload.length()-1);
        if (encodedModifiedPayload.endsWith("=")) encodedModifiedPayload = encodedModifiedPayload.substring(0, encodedModifiedPayload.length()-1);

        return jwtArr[0] + "." + encodedModifiedPayload + "." + jwtArr[2];
    }
}
