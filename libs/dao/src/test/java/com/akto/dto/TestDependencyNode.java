package com.akto.dto;

import com.akto.dao.context.Context;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertEquals;

public class TestDependencyNode {

    @Test
    public void testUpdateOrCreateParamInfo() {
        List<DependencyNode.ParamInfo> paramInfoList = new ArrayList<>();
        DependencyNode.ParamInfo paramInfo1 = new DependencyNode.ParamInfo("req2a", "resp1a", 2, false, false);
        paramInfoList.add(paramInfo1.copy());

        DependencyNode dependencyNode = new DependencyNode(
                "1", "url1", "GET", "2", "url2", "POST", paramInfoList, Context.now()
        );

        dependencyNode.updateOrCreateParamInfo(paramInfo1);
        assertEquals(1, dependencyNode.getParamInfos().size());
        assertEquals(paramInfo1.getCount()*2, dependencyNode.getParamInfos().get(0).getCount());


        DependencyNode.ParamInfo paramInfo2 = new DependencyNode.ParamInfo("req2b", "resp1b", 2, false, false);

        dependencyNode.updateOrCreateParamInfo(paramInfo2);
        assertEquals(2, dependencyNode.getParamInfos().size());
        assertEquals(paramInfo2.getRequestParam(), dependencyNode.getParamInfos().get(1).getRequestParam());
        assertEquals(paramInfo2.getResponseParam(), dependencyNode.getParamInfos().get(1).getResponseParam());
        assertEquals(paramInfo2.getCount(), dependencyNode.getParamInfos().get(1).getCount());
        assertEquals(paramInfo1.getCount()*2, dependencyNode.getParamInfos().get(0).getCount());

        dependencyNode.updateOrCreateParamInfo(paramInfo2);
        assertEquals(2, dependencyNode.getParamInfos().size());
        assertEquals(paramInfo2.getCount()*2, dependencyNode.getParamInfos().get(1).getCount());
        assertEquals(paramInfo1.getCount()*2, dependencyNode.getParamInfos().get(0).getCount());
    }

    @Test
    public void testGetHexId() {

        DependencyNode dependencyNode = new DependencyNode(
                "1", "url1", "GET", "2", "url2", "POST", new ArrayList<>(), Context.now()
        );
        String hexString = "6565aca2a945f1ba0d97c5cd";
        dependencyNode.setId(new ObjectId(hexString));

        assertEquals(hexString, dependencyNode.getHexId());
    }
}
