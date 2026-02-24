package com.akto.data_actor;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.akto.util.Constants;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestDbLayer extends MongoBasedTest {
    
    @Test
    public void testCreateCollectionSimpleForVpc_NewCollection() {
        // Test creating a new collection with VPC
        int vxlanId = 123;
        String vpcId = "vpc-123";

        DbLayer.createCollectionSimpleForVpc(vxlanId, vpcId, null, null);

        ApiCollection collection = ApiCollectionsDao.instance.findOne(Constants.ID, vxlanId);
        assertNotNull(collection);
        assertEquals(vxlanId, collection.getVxlanId());
        assertEquals(vpcId, collection.getUserSetEnvType());
        assertNotNull(collection.getStartTs());
        assertNotNull(collection.getUrls());
        assertTrue(collection.getUrls().isEmpty());
    }
    
    @Test
    public void testCreateCollectionSimpleForVpc_UpdateExistingCollection() {
        // Test updating an existing collection with new VPC
        int vxlanId = 456;
        String vpcId1 = "vpc-456";
        String vpcId2 = "vpc-789";

        // First create with vpcId1
        DbLayer.createCollectionSimpleForVpc(vxlanId, vpcId1, null, null);

        // Then update with vpcId2
        DbLayer.createCollectionSimpleForVpc(vxlanId, vpcId2, null, null);

        ApiCollection collection = ApiCollectionsDao.instance.findOne(Constants.ID, vxlanId);
        assertNotNull(collection);
        assertEquals(vxlanId, collection.getVxlanId());
        assertEquals(vpcId1 + ", " + vpcId2, collection.getUserSetEnvType());
    }
    
    @Test
    public void testCreateCollectionForHostAndVpc_NewCollection() {
        // Test creating a new collection with host and VPC
        String host = "example.com";
        int id = 789;
        String vpcId = "vpc-abc";

        DbLayer.createCollectionForHostAndVpc(host, id, vpcId, null, null);

        ApiCollection collection = ApiCollectionsDao.instance.findOne(ApiCollection.HOST_NAME, host);
        assertNotNull(collection);
        assertEquals(id, collection.getId());
        assertEquals(host, collection.getHostName());
        assertEquals(vpcId, collection.getUserSetEnvType());
        assertNotNull(collection.getStartTs());
        assertNotNull(collection.getUrls());
        assertTrue(collection.getUrls().isEmpty());
    }
    
    @Test
    public void testCreateCollectionForHostAndVpc_UpdateExistingCollection() {
        // Test updating an existing collection with new VPC
        String host = "test.com";
        int id = 101;
        String vpcId1 = "vpc-xyz";
        String vpcId2 = "vpc-uvw";

        // First create with vpcId1
        DbLayer.createCollectionForHostAndVpc(host, id, vpcId1, null, null);

        // Then update with vpcId2
        DbLayer.createCollectionForHostAndVpc(host, id, vpcId2, null, null);

        ApiCollection collection = ApiCollectionsDao.instance.findOne(ApiCollection.HOST_NAME, host);
        assertNotNull(collection);
        assertEquals(id, collection.getId());
        assertEquals(host, collection.getHostName());
        assertEquals(vpcId1 + ", " + vpcId2, collection.getUserSetEnvType());
    }
    
    @Test
    public void testCreateCollectionForHostAndVpc_NullVpcId() {
        // Test creating a collection with null VPC ID
        String host = "nullvpc.com";
        int id = 202;

        DbLayer.createCollectionForHostAndVpc(host, id, null, null, null);

        ApiCollection collection = ApiCollectionsDao.instance.findOne(ApiCollection.HOST_NAME, host);
        assertNotNull(collection);
        assertEquals(id, collection.getId());
        assertEquals(host, collection.getHostName());
        assertNull(collection.getUserSetEnvType());
    }

    @Test
    public void testCreateCollectionForHostAndVpc_NullVpcIdPreservesExistingEnvType() {
        // Test that null VPC ID doesn't modify existing userSetEnvType
        String host = "preserve-env.com";
        int id = 303;
        String existingVpcId = "vpc-existing";

        // First create with existing VPC ID
        DbLayer.createCollectionForHostAndVpc(host, id, existingVpcId, null, null);

        // Then try to update with null VPC ID
        DbLayer.createCollectionForHostAndVpc(host, id, null, null, null);

        ApiCollection collection = ApiCollectionsDao.instance.findOne(ApiCollection.HOST_NAME, host);
        assertNotNull(collection);
        assertEquals(id, collection.getId());
        assertEquals(host, collection.getHostName());
        // Verify the original VPC ID is preserved
        assertEquals(existingVpcId, collection.getUserSetEnvType());
    }
}
