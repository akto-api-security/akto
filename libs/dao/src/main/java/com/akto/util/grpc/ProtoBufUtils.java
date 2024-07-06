package com.akto.util.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class ProtoBufUtils {

    public static final String RAW_QUERY = "raw_query";
    public static final String DECODED_QUERY = "query";
    public static final String KEY_PREFIX = "param_";
    private final ObjectMapper mapper = new ObjectMapper();
    //Generic protobuf bytes
    private static final byte[] FIRST_BYTES = new byte[]{ 0, 0, 0, 0, 7};//Generic protobuf bytes
    private ProtoBufUtils() {
    }

    private static final ProtoBufUtils instance = new ProtoBufUtils();

    public static ProtoBufUtils getInstance() {
        return instance;
    }

    public Map<Object, Object> decodeProto(String encodedString) {
        byte[] originalByteArray = Base64.getDecoder().decode(encodedString);
        //Remove initial 5 bytes for unnecessary proto headers
        byte[] truncatedByteArray = new byte[originalByteArray.length - 5];
        for (int index = 5; index < originalByteArray.length; index++) {
            truncatedByteArray[index - 5] = originalByteArray[index];
        }
        return decodeProto(truncatedByteArray);
    }

    public Map<Object, Object> decodeProto(byte[] data) {
        return decodeProto(ByteString.copyFrom(data), 0);
    }

    public static Map<Object, Object> decodeProto(ByteString data, int depth) {
        final CodedInputStream input = CodedInputStream.newInstance(data.asReadOnlyByteBuffer());
        try {
            return decodeProtoInput(input, depth);
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    private static Map<Object, Object> decodeProtoInput(CodedInputStream input, int depth) throws IOException {
        HashMap<Object, Object> map = new HashMap<>();
        while (true) {
            final int tag = input.readTag();
            int type = WireFormat.getTagWireType(tag);
            if (tag == 0 || type == WireFormat.WIRETYPE_END_GROUP) {
                break;
            }

            final int number = WireFormat.getTagFieldNumber(tag);
            String keyPrefix = KEY_PREFIX + number;

            switch (type) {
                case WireFormat.WIRETYPE_VARINT:
                    map.put(keyPrefix, input.readInt64());
                    break;
                case WireFormat.WIRETYPE_FIXED64:
                    map.put(keyPrefix, Double.longBitsToDouble(input.readFixed64()));
                    break;
                case WireFormat.WIRETYPE_LENGTH_DELIMITED:
                    ByteString data = input.readBytes();
                    Map<Object, Object> subMessage = decodeProto(data, depth + 1);
                    if (data.size() < 30) {
                        boolean probablyString = true;
                        String str = new String(data.toByteArray(), StandardCharsets.UTF_8);
                        for (char c : str.toCharArray()) {
                            if (c < '\n') {
                                probablyString = false;
                                break;
                            }
                        }
                        if (probablyString) {
                            map.put(keyPrefix, str);
                        } else if (!subMessage.isEmpty()) {
                            map.put(keyPrefix, subMessage);
                        } else {
                            new String(data.toByteArray());
                        }
                    }
                    break;
                case WireFormat.WIRETYPE_START_GROUP:
                    map.put(keyPrefix, decodeProtoInput(input, depth + 1));
                    break;
                case WireFormat.WIRETYPE_FIXED32:
                    map.put(keyPrefix, Float.intBitsToFloat(input.readFixed32()));
                    break;
                default:
                    throw new InvalidProtocolBufferException("Invalid wire type");
            }
        }
        return map;
    }

    public static String base64EncodedJsonToProtobuf(String payload) throws IOException{
        Map<Object, Object> map = null;
        try {
            map = ProtoBufUtils.getInstance().mapper.readValue(payload, Map.class);
        } catch (Exception e) {
            map = new HashMap<>();
        }
        return base64EncodedJsonToProtobuf(map);
    }
    public static String base64EncodedJsonToProtobuf(Map<Object, Object> jsonMap) throws IOException{
        byte[] protobufArray = encodeJsonToProtobuf(jsonMap);
        byte[] finalArray = new byte[FIRST_BYTES.length + protobufArray.length];
        for (int index = 0; index < finalArray.length; index++) {
            if (index < FIRST_BYTES.length) {
                finalArray[index] = FIRST_BYTES[index];
            } else {
                finalArray[index] = protobufArray[index - FIRST_BYTES.length];
            }
        }
        return Base64.getEncoder().encodeToString(finalArray);
    }

    public static byte[] encodeJsonToProtobuf(Map<Object, Object> jsonMap) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(byteArrayOutputStream);

        encodeMapToProto(jsonMap, codedOutputStream);

        codedOutputStream.flush();
        return byteArrayOutputStream.toByteArray();
    }

    private static void encodeMapToProto(Map<Object, Object> map, CodedOutputStream codedOutputStream) throws IOException {
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();

            int number = Integer.parseInt(key.toString().replace(KEY_PREFIX, "")); // Replacing key-prefix

            codedOutputStream.writeTag(number, getWireType(value));

            if (value instanceof Long) {
                codedOutputStream.writeInt64NoTag((Long) value);
            } else if (value instanceof Double) {
                codedOutputStream.writeFixed64NoTag(Double.doubleToRawLongBits((Double) value));
            } else if (value instanceof String) {
                ByteString byteString = ByteString.copyFromUtf8((String) value);
                codedOutputStream.writeBytesNoTag(byteString);
            } else if (value instanceof Map) {
                byte[] nestedMessage = encodeJsonToProtobuf((Map<Object, Object>) value);
                codedOutputStream.writeBytesNoTag(ByteString.copyFrom(nestedMessage));
            } else if (value instanceof Float) {
                codedOutputStream.writeFixed32NoTag(Float.floatToIntBits((Float) value));
            } else {
                throw new IOException("Unsupported type: " + value.getClass().getName());
            }
        }
    }

    private static int getWireType(Object value) {
        if (value instanceof Long) {
            return WireFormat.WIRETYPE_VARINT;
        } else if (value instanceof Double) {
            return WireFormat.WIRETYPE_FIXED64;
        } else if (value instanceof String) {
            return WireFormat.WIRETYPE_LENGTH_DELIMITED;
        } else if (value instanceof Map) {
            return WireFormat.WIRETYPE_LENGTH_DELIMITED;
        } else if (value instanceof Float) {
            return WireFormat.WIRETYPE_FIXED32;
        } else {
            throw new IllegalArgumentException("Unsupported type: " + value.getClass().getName());
        }
    }
}