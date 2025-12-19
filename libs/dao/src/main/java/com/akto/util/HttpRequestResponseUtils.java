package com.akto.util;

import com.akto.util.grpc.ProtoBufUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.mongodb.BasicDBObject;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.util.Streams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;

import static com.akto.dto.OriginalHttpRequest.*;

public class HttpRequestResponseUtils {

    private final static ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(HttpRequestResponseUtils.class);

    public static final String FORM_URL_ENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded";
    public static final String GRPC_CONTENT_TYPE = "application/grpc";
    public static final String SOAP = "soap";
    public static final String XML = "xml";
    public static final String TEXT_EVENT_STREAM_CONTENT_TYPE = "text/event-stream";
    public static final String CONTENT_TYPE = "CONTENT-TYPE";
    public static final String HEADER_ACCEPT = "accept";
    public static final String APPLICATION_JSON = "application/json";
    public static final String MULTIPART_FORM_DATA_CONTENT_TYPE = "multipart/form-data";

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
            } else if (acceptableContentType.equals(MULTIPART_FORM_DATA_CONTENT_TYPE)) {
                // For multipart, we need the full header value with boundary parameter
                // getAcceptableContentType() returns only the constant, so get the actual header value
                String contentTypeHeader = getHeaderValue(requestHeaders, CONTENT_TYPE);
                String boundary = extractBoundary(contentTypeHeader);
                return convertMultipartToJson(rawRequest, boundary);
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
        List<String> acceptableContentTypes = Arrays.asList(JSON_CONTENT_TYPE, FORM_URL_ENCODED_CONTENT_TYPE, GRPC_CONTENT_TYPE, XML, SOAP, MULTIPART_FORM_DATA_CONTENT_TYPE);
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
        // Check if the input looks like form-encoded data
        // Form-encoded data should contain '=' and typically contains '&' or at least one key-value pair
        if (rawRequest == null || rawRequest.trim().isEmpty() || !rawRequest.contains("=")) {
            return rawRequest;
        }

        String myStringDecoded = null;
        try {
            myStringDecoded = URLDecoder.decode(rawRequest, "UTF-8");
        } catch (Exception e) {
            // If decoding fails, return the original string
            return rawRequest;
        }

        String[] parts = myStringDecoded.split("&");
        Map<String,String> valueMap = new HashMap<>();

        for(String part: parts){
            String[] keyVal = part.split("="); // The equal separates key and values
            if (keyVal.length == 2) {
                valueMap.put(keyVal[0], keyVal[1]);
            }
        }

        // If no valid key-value pairs were found, return the original string
        if (valueMap.isEmpty()) {
            return rawRequest;
        }

        try {
            return mapper.writeValueAsString(valueMap);
        } catch (Exception e) {
            // If JSON serialization fails, return the original string
            return rawRequest;
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
      /**
     * Extract boundary from Content-Type header
     * @param contentType Content-Type header value (e.g., "multipart/form-data; boundary=----WebKitFormBoundary...")
     * @return boundary string or null if not found
     */
      public static String extractBoundary(String contentType) {
        if (contentType == null) return null;
        // Parse "multipart/form-data; boundary=----WebKitFormBoundary..."
        String[] parts = contentType.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                return part.substring("boundary=".length()).trim();
            }
        }
        return null;
    }

    /**
     * Convert multipart/form-data body to JSON using Apache Commons FileUpload HIGH-LEVEL API
     * Uses FileItemIterator which handles ALL parsing internally (headers, boundaries, encoding)
     * 
     * @param rawRequest The raw multipart body
     * @param boundary The boundary string from Content-Type header
     * @return JSON string representation of the multipart data
     */
    public static String convertMultipartToJson(String rawRequest, String boundary) {
        if (boundary == null || rawRequest == null || rawRequest.isEmpty()) {
            return rawRequest;
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            
            // Use Apache Commons FileUpload's HIGH-LEVEL API - FileItemIterator
            // This handles ALL parsing: boundaries, headers, Content-Disposition, encoding, etc.
            // IMPORTANT: Use ISO-8859-1 to preserve binary data (rawRequest may contain binary bytes)
            byte[] bodyBytes = rawRequest.getBytes(StandardCharsets.ISO_8859_1);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bodyBytes);
            
            // Create FileUpload instance with security limits
            FileUpload fileUpload = new FileUpload();
            fileUpload.setHeaderEncoding("UTF-8");
            
            // Create a custom RequestContext for our byte array
            RequestContext ctx = new RequestContext() {
                @Override
                public String getCharacterEncoding() {
                    return "UTF-8";
                }
                
                @Override
                public String getContentType() {
                    return "multipart/form-data; boundary=" + boundary;
                }
                
                @Override
                public int getContentLength() {
                    return bodyBytes.length;
                }
                
                @Override
                public InputStream getInputStream() {
                    return inputStream;
                }
            };
            
            // Use FileItemIterator - handles ALL parsing automatically!
            FileItemIterator iterator = fileUpload.getItemIterator(ctx);
            
            int partCount = 0;
            while (iterator.hasNext()) {
                FileItemStream item = iterator.next();
                partCount++;
                
                // Apache Commons automatically parses field name from Content-Disposition!
                String fieldName = item.getFieldName();
                
                if (item.isFormField()) {
                    // Regular text field - Apache Commons handles encoding
                    String value = Streams.asString(item.openStream(), "UTF-8");
                    result.put(fieldName, value);
                } else {
                    // File upload - Apache Commons provides filename and content-type!
                    Map<String, String> fileObj = new LinkedHashMap<>();
                    fileObj.put("filename", item.getName());  // Automatically parsed!
                    fileObj.put("contentType", item.getContentType());  // Automatically parsed!
                    
                    // Read file content and Base64 encode
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    InputStream fileStream = item.openStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fileStream.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                    fileObj.put("content", Base64.getEncoder().encodeToString(output.toByteArray()));
                    
                    result.put(fieldName, fileObj);
                }
            }

            // If no parts were found, return empty JSON
            if (partCount == 0) {
                logger.warn("Apache Commons FileUpload found 0 parts in multipart request");
                return "{}";
            }

            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("Error converting multipart to JSON using Apache Commons FileUpload: " + e.getMessage(), e);
            // No fallback - return empty JSON on error
            return "{}";
        }
    }

    /**
     * Convert JSON back to multipart/form-data format
     * IMPORTANT: This returns a String but may contain binary data encoded as ISO-8859-1
     * to preserve byte values. Use getBytes(StandardCharsets.ISO_8859_1) to get the actual bytes.
     * 
     * @param jsonBody JSON string
     * @param boundary Boundary string to use
     * @return Multipart formatted string (with binary data preserved as ISO-8859-1)
     */
    public static String jsonToMultipart(String jsonBody, String boundary) {
        if (jsonBody == null || boundary == null) {
            return jsonBody;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> fields = mapper.readValue(jsonBody, LinkedHashMap.class);
            
            // Use ByteArrayOutputStream to handle binary data properly
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                // Write boundary
                outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));

                if (entry.getValue() instanceof Map) {
                    // File upload - handle binary data
                    @SuppressWarnings("unchecked")
                    Map<String, String> fileObj = (Map<String, String>) entry.getValue();
                    String filename = fileObj.get("filename");
                    String contentType = fileObj.getOrDefault("contentType", "application/octet-stream");
                    String base64Content = fileObj.get("content");

                    // Write headers
                    String headers = "Content-Disposition: form-data; name=\"" + 
                                   entry.getKey() + "\"; filename=\"" + filename + "\"\r\n" +
                                   "Content-Type: " + contentType + "\r\n\r\n";
                    outputStream.write(headers.getBytes(StandardCharsets.UTF_8));
                    
                    // Decode base64 and write raw bytes (preserves binary data)
                    if (base64Content != null && !base64Content.isEmpty()) {
                        byte[] decodedContent = Base64.getDecoder().decode(base64Content);
                        outputStream.write(decodedContent);
                    }
                } else {
                    // Text field
                    String headers = "Content-Disposition: form-data; name=\"" + 
                                   entry.getKey() + "\"\r\n\r\n";
                    outputStream.write(headers.getBytes(StandardCharsets.UTF_8));
                    outputStream.write(entry.getValue().toString().getBytes(StandardCharsets.UTF_8));
                }
                
                // Write CRLF after each part
                outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }

            // Write final boundary
            outputStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            
            // Convert to string using ISO-8859-1 to preserve all byte values
            // This is necessary because the result may contain binary data
            return outputStream.toString("ISO-8859-1");
            
        } catch (Exception e) {
            logger.error("Error converting JSON to multipart", e);
            return jsonBody;
        }
    }


}
