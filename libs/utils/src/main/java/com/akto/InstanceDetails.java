package com.akto;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;


public class InstanceDetails {

    public String instanceIp = null;
    public static final InstanceDetails instance = new InstanceDetails();
    private static final HttpClient httpclient = HttpClients.createDefault();
    private InstanceDetails() {}

    public void fetchInstanceDetails() {
        String url = "http://169.254.169.254/latest/meta-data/local-ipv4";
        HttpGet httpGet = new HttpGet(url);

        HttpResponse response = null;
        try {
            response = httpclient.execute(httpGet);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                instanceIp = EntityUtils.toString(entity);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

}
