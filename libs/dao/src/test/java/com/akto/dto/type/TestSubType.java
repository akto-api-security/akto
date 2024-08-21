package com.akto.dto.type;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.akto.dao.context.Context;
import org.junit.Test;

import com.akto.dto.AktoDataType;
import com.akto.dto.IgnoreData;
public class TestSubType {

    private final int ACCOUNT_ID = 1_000_000;
    public void testInitializer(){
        Context.accountId.set(ACCOUNT_ID);
        Map<String, AktoDataType> aktoDataTypeMap = new HashMap<>();
        aktoDataTypeMap.put("JWT", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("PHONE_NUMBER", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("CREDIT_CARD", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("IP_ADDRESS", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        AccountDataTypesInfo info = SingleTypeInfo.getAccountToDataTypesInfo().get(ACCOUNT_ID);
        if (info == null) {
            info = new AccountDataTypesInfo();
        }
        info.setAktoDataTypeMap(aktoDataTypeMap);
        SingleTypeInfo.getAccountToDataTypesInfo().put(ACCOUNT_ID, info);
    }

    @Test
    public void testNumberInString() {
        SingleTypeInfo.SubType happy = KeyTypes.findSubType("23423333333334", null,null);
        assertEquals(SingleTypeInfo.INTEGER_64, happy);
    }

    @Test
    public void testJWT() {
        testInitializer();
        SingleTypeInfo.SubType happy = KeyTypes.findSubType("eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmtpdGFAZ21haWwuY29tIiwiaWF0IjoxNjM0OTcxMTMxLCJleHAiOjE2MzUwNTc1MzF9.Ph4Jv-fdggwvnbdVViD9BWUReYL0dVfVGuMRz4d2oZNnYzWV0JCmjpB68p6k0yyPPua_yagIWVZf_oYH9PUgS7EuaPYR-Vg6uxKR1HuXRA6wb8Xf4RPoFjJYkhWoYmv38V9Cz2My9U85wgGHGZXEufu8ubrFmIfOP6-A39M4meNGw48f5oOz8V337SX45uPc6jE0EfmM4l9EbqFFCF0lRXbMMzn-ijsyXxLkI5npWnqtW3PAHC2Rs3FV40tkRqHYF-WM6SzyHLBh6bVeyeOsFRBoEjv-zFh8yrYnT6OvCa6jII2A6uj4MQ2k11-5bDBhfVPVc4hEQz37H_DWwtf23g", "",null);
        assertEquals(happy, SingleTypeInfo.JWT);
        SingleTypeInfo.SubType changeHeader =  KeyTypes.findSubType("eyJhbGciOiJSUzI1NiJ.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmtpdGFAZ21haWwuY29tIiwiaWF0IjoxNjM0OTcxMTMxLCJleHAiOjE2MzUwNTc1MzF9.Ph4Jv-fdggwvnbdVViD9BWUReYL0dVfVGuMRz4d2oZNnYzWV0JCmjpB68p6k0yyPPua_yagIWVZf_oYH9PUgS7EuaPYR-Vg6uxKR1HuXRA6wb8Xf4RPoFjJYkhWoYmv38V9Cz2My9U85wgGHGZXEufu8ubrFmIfOP6-A39M4meNGw48f5oOz8V337SX45uPc6jE0EfmM4l9EbqFFCF0lRXbMMzn-ijsyXxLkI5npWnqtW3PAHC2Rs3FV40tkRqHYF-WM6SzyHLBh6bVeyeOsFRBoEjv-zFh8yrYnT6OvCa6jII2A6uj4MQ2k11-5bDBhfVPVc4hEQz37H_DWwtf23g", "",null);
        assertEquals(changeHeader, SingleTypeInfo.GENERIC);
        SingleTypeInfo.SubType algMissingInHeader =  KeyTypes.findSubType("eyJhbGRnIjogIlJTMjU2In0.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmtpdGFAZ21haWwuY29tIiwiaWF0IjoxNjM0OTcxMTMxLCJleHAiOjE2MzUwNTc1MzF9.Ph4Jv-fdggwvnbdVViD9BWUReYL0dVfVGuMRz4d2oZNnYzWV0JCmjpB68p6k0yyPPua_yagIWVZf_oYH9PUgS7EuaPYR-Vg6uxKR1HuXRA6wb8Xf4RPoFjJYkhWoYmv38V9Cz2My9U85wgGHGZXEufu8ubrFmIfOP6-A39M4meNGw48f5oOz8V337SX45uPc6jE0EfmM4l9EbqFFCF0lRXbMMzn-ijsyXxLkI5npWnqtW3PAHC2Rs3FV40tkRqHYF-WM6SzyHLBh6bVeyeOsFRBoEjv-zFh8yrYnT6OvCa6jII2A6uj4MQ2k11-5bDBhfVPVc4hEQz37H_DWwtf23dgdd", "",null);
        assertEquals(algMissingInHeader, SingleTypeInfo.GENERIC);
        SingleTypeInfo.SubType invalidLength =  KeyTypes.findSubType("woiefjweofjweoifjweifjweiofjwiefjw", "",null);
        assertEquals(invalidLength, SingleTypeInfo.GENERIC);

    }


    @Test
    public void testPhoneNumber() {
        testInitializer();
        SingleTypeInfo.SubType happyIndian = KeyTypes.findSubType("+919967167961", "",null);
        assertEquals(happyIndian, SingleTypeInfo.PHONE_NUMBER);
        SingleTypeInfo.SubType wrongLength = KeyTypes.findSubType("+91996716796", "",null);
        assertEquals(wrongLength, SingleTypeInfo.GENERIC);
        SingleTypeInfo.SubType internation_spaces = KeyTypes.findSubType("+1 650 253 00 00", "",null);
        assertEquals(internation_spaces, SingleTypeInfo.PHONE_NUMBER);
        SingleTypeInfo.SubType international_dash = KeyTypes.findSubType("+1-541-754-3010", "",null);
        assertEquals(international_dash, SingleTypeInfo.PHONE_NUMBER);
    }


    @Test
    public void testCreditCard() {
        testInitializer();
        SingleTypeInfo.SubType subType = KeyTypes.findSubType("378282246310005", "",null);
        assertEquals(subType, SingleTypeInfo.CREDIT_CARD);
        subType = KeyTypes.findSubType(Long.valueOf("378282246310005"),"",null);
        assertEquals(subType, SingleTypeInfo.CREDIT_CARD);
        subType = KeyTypes.findSubType("5267 318 1879 75449","",null);
        assertEquals(subType, SingleTypeInfo.CREDIT_CARD);
        subType = KeyTypes.findSubType("5267-3181-8797-5449","",null);
        assertEquals(subType, SingleTypeInfo.CREDIT_CARD);
        subType = KeyTypes.findSubType("4111 1111 1111 1111","",null);
        assertEquals(subType, SingleTypeInfo.CREDIT_CARD);
        subType = KeyTypes.findSubType("5105 1051 0510 5100","",null);
        assertEquals(subType, SingleTypeInfo.CREDIT_CARD);
        subType = KeyTypes.findSubType("5104 0600 0000 0008","",null);
        assertEquals(subType, SingleTypeInfo.CREDIT_CARD);
        subType = KeyTypes.findSubType("4718 6091 0820 4366","",null);
        assertEquals(subType, SingleTypeInfo.CREDIT_CARD);
        subType = KeyTypes.findSubType("5104 0155 5555 5558","",null);
        assertEquals(subType, SingleTypeInfo.CREDIT_CARD);
        subType = KeyTypes.findSubType("5241 8100 0000 0000", "",null);
        assertEquals(subType, SingleTypeInfo.CREDIT_CARD);
        subType = KeyTypes.findSubType("3782822463100075", "",null);
        assertEquals(subType, SingleTypeInfo.INTEGER_64);
        subType = KeyTypes.findSubType("5241 8100 0000 A000", "",null);
        assertEquals(subType, SingleTypeInfo.GENERIC);
    }

    @Test
    public void testIP() {
        testInitializer();
        SingleTypeInfo.SubType happyIp4= KeyTypes.findSubType("172.8.9.28", "",null);
        assertEquals(happyIp4, SingleTypeInfo.IP_ADDRESS);
        SingleTypeInfo.SubType happyIp6= KeyTypes.findSubType("2001:0db8:85a3:0000:0000:8a2e:0370:7334", "",null);
        assertEquals(happyIp6, SingleTypeInfo.IP_ADDRESS);
        SingleTypeInfo.SubType octalIp4 = KeyTypes.findSubType("172.013.1.2", "",null);
        assertEquals(octalIp4, SingleTypeInfo.GENERIC);
        SingleTypeInfo.SubType negativeIp4 = KeyTypes.findSubType("172.8.-9.255", "",null);
        assertEquals(negativeIp4, SingleTypeInfo.GENERIC);
        SingleTypeInfo.SubType edgeCase = KeyTypes.findSubType("172.01.1.2","",null);
        assertEquals(edgeCase, SingleTypeInfo.GENERIC);
        SingleTypeInfo.SubType ipv4_mapped_ipv6= KeyTypes.findSubType("0000:0000:0000:0000:0000:ffff:192.168.100.228", "",null);
        assertEquals(ipv4_mapped_ipv6, SingleTypeInfo.IP_ADDRESS);
    }

}
