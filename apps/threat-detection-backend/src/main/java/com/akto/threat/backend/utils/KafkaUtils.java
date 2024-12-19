package com.akto.threat.backend.utils;

import com.google.gson.Gson;
import com.mongodb.BasicDBObject;

public class KafkaUtils {

  private static final Gson gson = new Gson();

  public static String generateMsg(Object writes, String eventType, String accountId) {
    BasicDBObject obj = new BasicDBObject();
    obj.put("eventType", eventType);
    String payloadStr = gson.toJson(writes);
    obj.put("payload", payloadStr);
    obj.put("accountId", accountId);

    return obj.toString();
  }
}
