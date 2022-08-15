package com.akto.action.misc;

import com.akto.action.UserAction;
import com.akto.dao.OtpMessagesDao;
import com.akto.dao.context.Context;
import com.akto.dto.OTPMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OtpAction extends UserAction {


    private String from;
    private String text;
    @Override
    public String execute() {
        Context.accountId.set(1_000_000);

        System.out.println(text);
        if (text == null || !text.contains("OTP")) {
            System.out.println("But doesn't contain the word 'OTP' ");
            return SUCCESS.toUpperCase();
        }

        System.out.println("And contains OTP");
        OTPMessage otpMessage = new OTPMessage(Context.now(), from, text, Context.now());
        OtpMessagesDao.instance.insertOne(otpMessage);
        return SUCCESS.toUpperCase();
    }

    private String otp;
    public String fetchRecentOtp() {
        Context.accountId.set(1_000_000);
        List<OTPMessage> OTPMessageList = OtpMessagesDao.instance.findAll(Filters.gte("timestamp", Context.now() - 90));
        if (OTPMessageList.isEmpty()) return Action.ERROR.toUpperCase();

        OTPMessage otpMessage = OTPMessageList.get(OTPMessageList.size()-1); // latest

        String val = extractOtp(otpMessage.getMessage());
        if (val == null || val.isEmpty()) return ERROR.toUpperCase();

        otp = val;
        System.out.println("found otp: " + otp);

        return SUCCESS.toUpperCase();
    }

    private String extractOtp(String message) {
        if (!message.contains("OTP") && !message.contains("otp")) return null;
        Pattern pattern = Pattern.compile("(\\d{6})");
        Matcher matcher = pattern.matcher(message);
        String val = null;
        if (matcher.find()) {
            val = matcher.group(0);
        }

        return val;
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    private Integer latestMessageId = null;
    public String fetchLatestMessageId() {
        BasicDBObject result;
        try {
            result = makeRequestToMySms();
            System.out.println("****");
            System.out.println(result);
            System.out.println("****");

            List<Map> messages = (List<Map>) result.get("messages");
            if (messages.size() == 0) return SUCCESS.toUpperCase();

            latestMessageId = (Integer) messages.get(0).get("messageId");
        } catch (Exception e) {
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    private Integer lastMessageId;
    public String fetchOtpFromMySms() {
        try {
            BasicDBObject result = makeRequestToMySms();

            System.out.println("((((");
            System.out.println(result);
            System.out.println("((((");

            List<Map> messages = (List<Map>) result.get("messages");

            Integer messageId = (Integer) messages.get(0).get("messageId");
            if (Objects.equals(messageId, lastMessageId)) return ERROR.toUpperCase();

            String message = (String) messages.get(0).get("message");

            String val = extractOtp(message);
            if (val == null || val.isEmpty()) return ERROR.toUpperCase();

            otp = val;
            System.out.println("found otp: " + otp);

        } catch (Exception e) {
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    private String apiKey ;
    private String authToken;
    private String address;
    private BasicDBObject makeRequestToMySms() throws Exception {
        String path = "https://app.mysms.com/json/user/message/get/by/conversation";

        URL url = new URL(path);
        URLConnection con = url.openConnection();
        HttpURLConnection http = (HttpURLConnection)con;
        http.setRequestMethod("POST"); // PUT is another valid option
        http.setDoOutput(true);

        BasicDBObject req = new BasicDBObject();
        req.put("apiKey", apiKey);
        req.put("authToken", authToken);
        req.put("address", address);

        String json = req.toJson();

        http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        http.connect();
        try(OutputStream os = http.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        System.out.println(http.getResponseCode());
        InputStream inputStream = http.getInputStream();


        return mapper.readValue(inputStream, BasicDBObject.class);
    }

    public String getOtp() {
        return otp;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setLastMessageId(Integer lastMessageId) {
        this.lastMessageId = lastMessageId;
    }

    public Integer getLatestMessageId() {
        return latestMessageId;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
