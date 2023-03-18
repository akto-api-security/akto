package com.akto.util.grpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.WireFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class ProtoBufUtils {

    public static final String RAW_QUERY = "raw_query";
    public static final String DECODED_QUERY = "query";
    public static final String KEY_PREFIX = "param_";
    private ProtoBufUtils() {}
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
                        } else if (!subMessage.isEmpty()){
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
}
