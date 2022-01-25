package com.akto.dto.type;

import static com.akto.dto.type.KeyTypes.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TestSubType {

    @Test
    public void testJWT() {
        boolean happy =  isJWT("eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmtpdGFAZ21haWwuY29tIiwiaWF0IjoxNjM0OTcxMTMxLCJleHAiOjE2MzUwNTc1MzF9.Ph4Jv-fdggwvnbdVViD9BWUReYL0dVfVGuMRz4d2oZNnYzWV0JCmjpB68p6k0yyPPua_yagIWVZf_oYH9PUgS7EuaPYR-Vg6uxKR1HuXRA6wb8Xf4RPoFjJYkhWoYmv38V9Cz2My9U85wgGHGZXEufu8ubrFmIfOP6-A39M4meNGw48f5oOz8V337SX45uPc6jE0EfmM4l9EbqFFCF0lRXbMMzn-ijsyXxLkI5npWnqtW3PAHC2Rs3FV40tkRqHYF-WM6SzyHLBh6bVeyeOsFRBoEjv-zFh8yrYnT6OvCa6jII2A6uj4MQ2k11-5bDBhfVPVc4hEQz37H_DWwtf23g");
        assertTrue(happy);
        boolean changeHeader =  isJWT("eyJhbGciOiJSUzI1NiJ.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmtpdGFAZ21haWwuY29tIiwiaWF0IjoxNjM0OTcxMTMxLCJleHAiOjE2MzUwNTc1MzF9.Ph4Jv-fdggwvnbdVViD9BWUReYL0dVfVGuMRz4d2oZNnYzWV0JCmjpB68p6k0yyPPua_yagIWVZf_oYH9PUgS7EuaPYR-Vg6uxKR1HuXRA6wb8Xf4RPoFjJYkhWoYmv38V9Cz2My9U85wgGHGZXEufu8ubrFmIfOP6-A39M4meNGw48f5oOz8V337SX45uPc6jE0EfmM4l9EbqFFCF0lRXbMMzn-ijsyXxLkI5npWnqtW3PAHC2Rs3FV40tkRqHYF-WM6SzyHLBh6bVeyeOsFRBoEjv-zFh8yrYnT6OvCa6jII2A6uj4MQ2k11-5bDBhfVPVc4hEQz37H_DWwtf23g");
        assertFalse(changeHeader);
        boolean algMissingInHeader =  isJWT("eyJhbGRnIjogIlJTMjU2In0.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmtpdGFAZ21haWwuY29tIiwiaWF0IjoxNjM0OTcxMTMxLCJleHAiOjE2MzUwNTc1MzF9.Ph4Jv-fdggwvnbdVViD9BWUReYL0dVfVGuMRz4d2oZNnYzWV0JCmjpB68p6k0yyPPua_yagIWVZf_oYH9PUgS7EuaPYR-Vg6uxKR1HuXRA6wb8Xf4RPoFjJYkhWoYmv38V9Cz2My9U85wgGHGZXEufu8ubrFmIfOP6-A39M4meNGw48f5oOz8V337SX45uPc6jE0EfmM4l9EbqFFCF0lRXbMMzn-ijsyXxLkI5npWnqtW3PAHC2Rs3FV40tkRqHYF-WM6SzyHLBh6bVeyeOsFRBoEjv-zFh8yrYnT6OvCa6jII2A6uj4MQ2k11-5bDBhfVPVc4hEQz37H_DWwtf23dgdd");
        assertFalse(algMissingInHeader);
        boolean invalidLength =  isJWT("woiefjweofjweoifjweifjweiofjwiefjw");
        assertFalse(invalidLength);

    }


    @Test
    public void testPhoneNumber() {
        boolean happyIndian = isPhoneNumber("+919967167961");
        assertTrue(happyIndian);
        boolean wrongLength = isPhoneNumber("+91996716796");
        assertFalse(wrongLength);
        boolean internation_spaces = isPhoneNumber("+1 650 253 00 00");
        assertTrue(internation_spaces);
        boolean international_dash = isPhoneNumber("+1-541-754-3010");
        assertTrue(international_dash);
    }


    @Test
    public void testCreditCard() {
        boolean happy = isCreditCard("378282246310005");
        assertTrue(happy);
        boolean sad = isCreditCard("3782822463100075");
        assertFalse(sad);
    }

    @Test
    public void testIP() {
        boolean happyIp4= isIP("172.8.9.28");
        assertTrue(happyIp4);
        boolean happyIp6= isIP("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        assertTrue(happyIp6);
        boolean octalIp4 = isIP("172.013.1.2");
        assertFalse(octalIp4);
        boolean negativeIp4 = isIP("172.8.-9.255");
        assertFalse(negativeIp4);
        boolean edgeCase = isIP("172.01.1.2");
        assertFalse(edgeCase);
    }

}
