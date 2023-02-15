package com.akto.notifications.util;

import com.akto.dao.context.Context;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GraphFetcher {

    public static void saveSingleTrendGraph(String destinationFile, String timezone) {

        try {
            HttpClient httpclient = HttpClients.createDefault();
            String highchartServerURL = "http://export.highcharts.com/";
            HttpPost httppost = new HttpPost(highchartServerURL);


            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("async", "true"));
            params.add(new BasicNameValuePair("type", "png"));
            params.add(new BasicNameValuePair("width", "1000"));

            BasicDBList series = new BasicDBList();
            BasicDBObject object = new BasicDBObject("series", series);
            BasicDBList numbers = new BasicDBList();

            
            BasicDBList xAxis = new BasicDBList();
            xAxis.add(BasicDBObject.parse("            {" +
                "              type: 'datetime'," +
                "              dateTimeLabelFormats: {" +
                "                day: '%b %e'," +
                "                month: '%b'" +
                "              }," +
                "              title: 'Date'," +
                "              visible: 'false'," +
                "              gridLineWidth: '0px'" +
                "            }"));

            object.put("xAxis", xAxis);

            series.add(new BasicDBObject("dataLabels", new BasicDBObject("enabled", "true")).append("data", numbers));

            params.add(new BasicNameValuePair("options", object.toJson()));
            params.add(new BasicNameValuePair("globalOptions", "{\"lang\":{\"thousandsSep\":\",\"}}"));
            httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

            HttpResponse response = httpclient.execute(httppost);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                String content =  EntityUtils.toString(entity);
                System.out.println(highchartServerURL+content);
                ImageFetcher.saveImage(highchartServerURL+content, destinationFile);
            }

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
