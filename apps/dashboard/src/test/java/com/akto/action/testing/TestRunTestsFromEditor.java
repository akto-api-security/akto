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

        String validRemoteUrl = "https://akto-setup.s3.amazonaws.com/templates/128x128.png";
        String uploadUrl = "https://juiceshop.akto.io/profile/image/file";

        RequestBody requestBody = ApiExecutor.getFileRequestBody(validRemoteUrl);
        MediaType contentType = requestBody.contentType();
        assertEquals(-1 , requestBody.contentLength());
        assertEquals("multipart", contentType.type());
        
    }
    
}
