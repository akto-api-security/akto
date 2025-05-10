package com.akto.testing;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.type.RequestTemplate;
import com.akto.runtime.URLAggregator;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class ApiExecutorTest {

    @Test
    public void kafkainteg() throws Exception {
        KafkaUtils kafkaUtils = new KafkaUtils();
        kafkaUtils.initKafkaProducer();
        kafkaUtils.insertData("https://xyz.com/api/books", "reqheaders", "respHeaders",
                "POST", "reqPayload", "respPayload", "ip1", "12324",
                "201", "type1", "status1", "acc1", "vxlan1",
                "false", "HAR");
    }

}