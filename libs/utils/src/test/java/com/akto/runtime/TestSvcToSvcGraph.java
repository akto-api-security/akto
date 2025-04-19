package com.akto.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.graph.K8sDaemonsetGraphParams;
import com.akto.dto.graph.SvcToSvcGraph;
import com.akto.dto.graph.SvcToSvcGraphParams;

public class TestSvcToSvcGraph extends MongoBasedTest {


    private SvcToSvcGraphParams in(String serviceName) {
        return new K8sDaemonsetGraphParams(serviceName, serviceName, "1324", "some_id", "1");
    }

    private SvcToSvcGraphParams out(String sourceServiceName, String targetServiceName) {
        return new K8sDaemonsetGraphParams(targetServiceName, sourceServiceName, "1324", "some_id", "2");
    }

    @Test
    public void testAlgo() {
        DataActor dataActor = DataActorFactory.fetchInstance();
        SvcToSvcGraphManager svcToSvcGraphManager = SvcToSvcGraphManager.createFromEdgesAndNodes(dataActor);


        svcToSvcGraphManager.processRecord(in("api.gateway.com"));
        svcToSvcGraphManager.processRecord(in("api.details.com"));
        svcToSvcGraphManager.processRecord(in("api.reviews.com"));
        svcToSvcGraphManager.processRecord(in("api.google.com"));
        svcToSvcGraphManager.processRecord(out("api.gateway.com", "api.details.com"));
        svcToSvcGraphManager.processRecord(out("api.gateway.com", "api.reviews.com"));
        svcToSvcGraphManager.processRecord(out("api.reviews.com", "api.google.com"));

        SvcToSvcGraph changes = svcToSvcGraphManager.updateWithNewDataAndReturnDelta(dataActor);

        changes.getNodes().forEach(node -> {
            System.out.println("Node: " + node.getId());
        });

        changes.getEdges().forEach(edge -> {
            System.out.println("Edge: " + edge.getSource() + " -> " + edge.getTarget());
        });

        assertEquals(4, changes.getNodes().size());
        assertEquals(3, changes.getEdges().size());
    }
    
}
