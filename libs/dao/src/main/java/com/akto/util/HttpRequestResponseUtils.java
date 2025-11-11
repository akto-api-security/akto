package com.akto.util;

import com.akto.dao.context.Context;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.types.CappedSet;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.grpc.ProtoBufUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.mongodb.BasicDBObject;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;
import javax.xml.transform.*;

import static com.akto.dto.OriginalHttpRequest.*;

public class HttpRequestResponseUtils {

    private final static ObjectMapper mapper = new ObjectMapper();

    public static final String FORM_URL_ENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded";
    public static final String GRPC_CONTENT_TYPE = "application/grpc";
    public static final String TEXT_EVENT_STREAM_CONTENT_TYPE = "text/event-stream";
    public static final String CONTENT_TYPE = "CONTENT-TYPE";
    public static final String APPLICATION_JSON = "application/json";
    public static final String APPLICATION_XML = "application/xml";
    public static final String APPLICATION_SOAP_XML = "application/soap+xml";

    public static final String HEADER_ACCEPT = "accept";

    public static List<SingleTypeInfo> generateSTIsFromPayload(int apiCollectionId, String url, String method,String body, int responseCode) {
        int now = Context.now();
        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();
        Map<String, Set<Object>> respFlattened = extractValuesFromPayload(body);
        for (String param: respFlattened.keySet()) {
            // values is basically the type
            Set<Object> values = respFlattened.get(param);
            if (values == null || values.isEmpty()) continue;

            ArrayList<Object> valuesList = new ArrayList<>(values);
            String val = valuesList.get(0) == null ? null : valuesList.get(0).toString();
            SingleTypeInfo.SubType subType = findSubType(val);
            SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, method,responseCode, false, param, subType, apiCollectionId, false);
            SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0, now, 0, new CappedSet<>(), SingleTypeInfo.Domain.ANY, Long.MAX_VALUE, Long.MIN_VALUE);
            singleTypeInfos.add(singleTypeInfo);
        }

        return singleTypeInfos;
    }

    public static SingleTypeInfo.SubType findSubType(String val) {
        if (val == null) return SingleTypeInfo.GENERIC;
        if (val.equalsIgnoreCase("short") || val.equalsIgnoreCase("int")) return  SingleTypeInfo.INTEGER_32;
        if (val.equalsIgnoreCase("long")) return  SingleTypeInfo.INTEGER_64;
        if (val.equalsIgnoreCase("float") || val.equalsIgnoreCase("double")) return  SingleTypeInfo.FLOAT;
        if (val.equalsIgnoreCase("boolean")) return  SingleTypeInfo.TRUE;

        return SingleTypeInfo.GENERIC;
    }
    public static final String SOAP = "soap";
    public static final String XML = "xml";

    public static Map<String, Set<Object>> extractValuesFromPayload(String body) {
        if (body == null) return new HashMap<>();
        if (body.startsWith("[")) body = "{\"json\": "+body+"}";
        BasicDBObject respPayloadObj;
        try {
            respPayloadObj = BasicDBObject.parse(body);
        } catch (Exception e) {
            respPayloadObj = BasicDBObject.parse("{}");
        }
        return JSONUtils.flatten(respPayloadObj);
    }

    public static String rawToJsonString(String rawRequest, Map<String,List<String>> requestHeaders) {
        if (rawRequest == null) return null;
        rawRequest = rawRequest.trim();
        String acceptableContentType = getAcceptableContentType(requestHeaders);
        if (acceptableContentType != null && rawRequest.length() > 0) {
            // only if request payload is of FORM_URL_ENCODED_CONTENT_TYPE we convert it to json
            if (acceptableContentType.equals(FORM_URL_ENCODED_CONTENT_TYPE)) {
                return convertFormUrlEncodedToJson(rawRequest);
            } else if (acceptableContentType.equals(GRPC_CONTENT_TYPE)) {
                return convertGRPCEncodedToJson(rawRequest);
            } else if (acceptableContentType.contains(XML) || acceptableContentType.contains(SOAP) ) {
                return convertXmlToJson(rawRequest);
            }
        }

        return rawRequest;
    }

    public static String convertGRPCEncodedToJson(byte[] rawRequest) {
        String base64 = Base64.getEncoder().encodeToString(rawRequest);

        // empty grpc response, only headers present
        if (rawRequest.length <= 5) {
            return "{}";
        }

        try {
            Map<Object, Object> map = ProtoBufUtils.getInstance().decodeProto(rawRequest);
            if (map.isEmpty()) {
                return base64;
            }
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return base64;
        }
    }

    private static ObjectMapper objectMapper = new ObjectMapper();
    private static XmlMapper xmlMapper = new XmlMapper();

    public static String convertXmlToJson(String rawRequest) {
        try {
            String removeXmlLine = rawRequest.replaceFirst("<\\?xml.*?\\?>", "").replaceAll("<(/?)(\\w+):(\\w+)([^>]*)>", "<$1$3$4>").trim();
            JsonNode rootNode = xmlMapper.readTree(removeXmlLine);
            JsonNode bodyNode = rootNode.get("Body");
            if (bodyNode == null) {
                bodyNode = rootNode.get("body");
            }

            if (bodyNode == null) {
                bodyNode = rootNode;
            }
            return objectMapper.writeValueAsString(bodyNode);
        } catch (Exception e) {
            return rawRequest;
        }
    }

    public static String updateXmlWithModifiedJson(String originalXml, String modifiedJson) throws Exception {
        originalXml = originalXml.replaceFirst("<\\?xml.*?\\?>", "").replaceAll("<(/?)(\\w+):(\\w+)([^>]*)>", "<$1$3$4>").trim();
        ObjectMapper objectMapper = new ObjectMapper();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(originalXml)));

        NodeList bodyNodes = doc.getElementsByTagNameNS("*", "Body");
        boolean hasBody = bodyNodes.getLength() > 0;
        Element targetElement;

        if (hasBody) {
            targetElement = (Element) bodyNodes.item(0);

            // Clear existing content inside Body
            while (targetElement.hasChildNodes()) {
                targetElement.removeChild(targetElement.getFirstChild());
            }
        } else {
            // No Body, target the document root itself
            targetElement = doc.getDocumentElement();

            // Clear existing content under root
            while (targetElement.hasChildNodes()) {
                targetElement.removeChild(targetElement.getFirstChild());
            }
        }

        if (modifiedJson != null && modifiedJson.startsWith("<")) {
            return modifiedJson;
        }

        JsonNode modifiedBody = objectMapper.readTree(modifiedJson);

        if (modifiedBody.isTextual()) {
            String xmlContent = modifiedBody.asText();
            Document tempDoc = builder.parse(new InputSource(new StringReader(xmlContent)));
            Node importedNode = doc.importNode(tempDoc.getDocumentElement(), true);
            targetElement.appendChild(importedNode);
        } else {
            appendJsonToXml(modifiedBody, doc, targetElement);
        }

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private static void appendJsonToXml(JsonNode jsonNode, Document doc, Element parent) {
        if (jsonNode.isObject()) {
            jsonNode.fields().forEachRemaining(field -> {
                Element child = doc.createElement(field.getKey());
                appendJsonToXml(field.getValue(), doc, child);
                parent.appendChild(child);
            });
        } else if (jsonNode.isArray()) {
            jsonNode.forEach(item -> {
                Element itemElement = doc.createElement("item");
                appendJsonToXml(item, doc, itemElement);
                parent.appendChild(itemElement);
            });
        } else {
            parent.setTextContent(jsonNode.asText());
        }
    }


    public static String convertGRPCEncodedToJson(String rawRequest) {
        try {
            Map<Object, Object> map = ProtoBufUtils.getInstance().decodeProto(rawRequest);
            if (map.isEmpty()) {
                return rawRequest;
            }
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return rawRequest;
        }
    }
    
    public static String getAcceptableContentType(Map<String,List<String>> headers) {
        List<String> acceptableContentTypes = Arrays.asList(JSON_CONTENT_TYPE, FORM_URL_ENCODED_CONTENT_TYPE, GRPC_CONTENT_TYPE, XML, SOAP);
        List<String> contentTypeValues;
        if (headers == null) return null;
        contentTypeValues = headers.get("content-type");
        if (contentTypeValues != null) {
            for (String value: contentTypeValues) {
                for (String acceptableContentType: acceptableContentTypes) {
                    if (value.contains(acceptableContentType)) {
                        return acceptableContentType;
                    }
                }
            }
        }
        return null;
    }
    
    public static String convertFormUrlEncodedToJson(String rawRequest) {
        String myStringDecoded = null;
        try {
            myStringDecoded = URLDecoder.decode(rawRequest, "UTF-8");
        } catch (Exception e) {
            return null;
        }
        String[] parts = myStringDecoded.split("&");
        Map<String,String> valueMap = new HashMap<>();

        for(String part: parts){
            String[] keyVal = part.split("="); // The equal separates key and values
            if (keyVal.length == 2) {
                valueMap.put(keyVal[0], keyVal[1]);
            }
        }
        try {
            return mapper.writeValueAsString(valueMap);
        } catch (Exception e) {
            return null;
        }
    }

    public static String jsonToFormUrlEncoded(String requestPayload) {
        JSONObject jsonObject = new JSONObject(requestPayload);

        StringBuilder formUrlEncoded = new StringBuilder();

        // Iterate over the keys in the JSON object
        for (String key : jsonObject.keySet()) {
            // Encode the key and value, and append them to the string builder
            try {
                String tmp = encode(key) + "=" + encode(String.valueOf(jsonObject.get(key))) + "&";
                formUrlEncoded.append(tmp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Remove the last "&"
        if (formUrlEncoded.length() > 0) {
            formUrlEncoded.setLength(formUrlEncoded.length() - 1);
        }

        return formUrlEncoded.toString();
    }
    public static String encode(String s) throws UnsupportedEncodingException {

        /*
         * No need to reverse the encoding for application/x-www-form-urlencoded  
         * Ref: https://www.w3.org/TR/html401/interact/forms.html#h-17.13.4.1
         */

        return URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.name());
                // .replaceAll("\\+", "%20")
                // .replaceAll("\\%21", "!")
                // .replaceAll("\\%27", "'")
                // .replaceAll("\\%28", "(")
                // .replaceAll("\\%29", ")")
                // .replaceAll("\\%7E", "~")
                // .replaceAll("\\%5B", "[")
                // .replaceAll("\\%5D", "]");
    }

    public static Map<String, String> decryptRequestPayload(String rawRequest){
        Map<String, String> decryptedMap = new HashMap<>();
        if(!StringUtils.isEmpty(rawRequest)){
            rawRequest = rawRequest.trim();
            String decodedString = rawRequest;
            if(rawRequest.startsWith("ey", 0)){ // since jwt starts with ey as base64 encoded string of '{' is needed to be proper json
                try {
                    String[] jwtParts = rawRequest.split("\\.");
                    if(jwtParts.length == 3) {
                        String payload = jwtParts[1];
                        byte[] decodedBytes = Base64.getDecoder().decode(payload);
                        decodedString = new String(decodedBytes);
                        decryptedMap.put("type", GlobalEnums.ENCODING_TYPE.JWT.name());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            decryptedMap.put("payload", decodedString);
        }
        return decryptedMap;
    }

    /**
     * Get header value from headers map using case-insensitive comparison
     * @param headers The headers map
     * @param headerName The header name to search for
     * @return The first value of the header if found, null otherwise
     */
    public static String getHeaderValue(Map<String, List<String>> headers, String headerName) {
        if (headers == null || headers.isEmpty() || headerName == null) {
            return null;
        }

        for (String key : headers.keySet()) {
            if (key != null && key.equalsIgnoreCase(headerName)) {
                List<String> values = headers.get(key);
                if (values != null && !values.isEmpty()) {
                    return values.get(0);
                }
            }
        }

        return null;
    }
}
