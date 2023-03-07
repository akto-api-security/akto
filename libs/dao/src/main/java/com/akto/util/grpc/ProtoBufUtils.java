package com.akto.util.grpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.WireFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class ProtoBufUtils {

    public static final String RAW_QUERY = "raw_query";
    private ProtoBufUtils() {}
    private static final ProtoBufUtils instance = new ProtoBufUtils();
    public static ProtoBufUtils getInstance() {
        return instance;
    }

    public HashMap<Object, Object> decodeProto(byte[] data) {
        return decodeProto(ByteString.copyFrom(data), 0);
    }

    public static HashMap<Object, Object> decodeProto(ByteString data, int depth) {
        final CodedInputStream input = CodedInputStream.newInstance(data.asReadOnlyByteBuffer());
        try {
            return decodeProtoInput(input, depth);
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    private static HashMap<Object, Object> decodeProtoInput(CodedInputStream input, int depth) throws IOException {
        HashMap<Object, Object> map = new HashMap<>();
        while (true) {
            final int tag = input.readTag();
            int type = WireFormat.getTagWireType(tag);
            if (tag == 0 || type == WireFormat.WIRETYPE_END_GROUP) {
                break;
            }

            final int number = WireFormat.getTagFieldNumber(tag);

            switch (type) {
                case WireFormat.WIRETYPE_VARINT:
                    map.put(number, input.readInt64());
                    break;
                case WireFormat.WIRETYPE_FIXED64:
                    map.put(number, Double.longBitsToDouble(input.readFixed64()));
                    break;
                case WireFormat.WIRETYPE_LENGTH_DELIMITED:
                    ByteString data = input.readBytes();
                    HashMap<Object, Object> submessage = decodeProto(data, depth + 1);
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
                            map.put(number, str);
                        } else if (!submessage.isEmpty()){
                            map.put(number, submessage);
                        } else {
                            new String(data.toByteArray());
                        }
                    }
                    break;
                case WireFormat.WIRETYPE_START_GROUP:
                    map.put(number, decodeProtoInput(input, depth + 1));
                    break;
                case WireFormat.WIRETYPE_FIXED32:
                    map.put(number, Float.intBitsToFloat(input.readFixed32()));
                    break;
                default:
                    throw new InvalidProtocolBufferException("Invalid wire type");
            }
        }
        return map;
    }
}
