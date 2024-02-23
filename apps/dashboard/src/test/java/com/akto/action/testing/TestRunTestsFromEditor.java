package com.akto.action.testing;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.testing.ApiExecutor;

import okhttp3.MediaType;
import okhttp3.RequestBody;

public class TestRunTestsFromEditor extends MongoBasedTest {

    @Test
    public void TestAttachFileOperator() throws Exception{

        String validRemoteUrl = "https://temp-aryan.s3.ap-south-1.amazonaws.com/big_image.jpeg";
        String uploadUrl = "https://juiceshop.akto.io/profile/image/file";

        RequestBody requestBody = ApiExecutor.getFileRequestBody(validRemoteUrl);
        MediaType contentType = requestBody.contentType();
        assertEquals(-1 , requestBody.contentLength());
        assertEquals("multipart", contentType.type());
        
    }
    
}
