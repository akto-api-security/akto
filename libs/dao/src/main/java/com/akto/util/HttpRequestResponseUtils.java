package com.akto.util;

import com.akto.util.grpc.ProtoBufUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.mongodb.BasicDBObject;
import org.json.JSONObject;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.transform.*;

import static com.akto.dto.OriginalHttpRequest.*;

public class HttpRequestResponseUtils {

    private final static ObjectMapper mapper = new ObjectMapper();

    public static final String FORM_URL_ENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded";
    public static final String GRPC_CONTENT_TYPE = "application/grpc";
    public static final String SOAP = "soap";
    public static final String XML = "xml";
    public static final String TEXT_EVENT_STREAM_CONTENT_TYPE = "text/event-stream";
    public static final String CONTENT_TYPE = "CONTENT-TYPE";
    public static final String HEADER_ACCEPT = "accept";

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
            String removeXmlLine = rawRequest.replaceFirst("<\\?xml.*?\\?>", "").trim();
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
        for (String k: headers.keySet()) {
            if (k.equalsIgnoreCase("content-type")) {
                contentTypeValues = headers.get(k);
                for (String value: contentTypeValues) {
                    for (String acceptableContentType: acceptableContentTypes) {
                        if (value.contains(acceptableContentType)) {
                            return acceptableContentType;
                        }
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
        return URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.name())
                .replaceAll("\\+", "%20")
                .replaceAll("\\%21", "!")
                .replaceAll("\\%27", "'")
                .replaceAll("\\%28", "(")
                .replaceAll("\\%29", ")")
                .replaceAll("\\%7E", "~")
                .replaceAll("\\%5B", "[")
                .replaceAll("\\%5D", "]");
    }

    public static String updateXmlWithModifiedJson(String originalXml, String modifiedJson) throws Exception {
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

}
