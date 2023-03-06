package com.akto.grpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.WireFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ProtoBufUtils {

    public static final String GRPC_CONTENT_TYPE = "application/grpc";
    private ProtoBufUtils() {}
    private static final ProtoBufUtils instance = new ProtoBufUtils();
    public static ProtoBufUtils getInstance() {
        return instance;
    }

    public String decodeProto(byte[] data, boolean singleLine) throws IOException {
        return decodeProto(ByteString.copyFrom(data), 0, singleLine);
    }

    public static String decodeProto(ByteString data, int depth, boolean singleLine) throws IOException {
        final CodedInputStream input = CodedInputStream.newInstance(data.asReadOnlyByteBuffer());
        return decodeProtoInput(input, depth, singleLine);
    }

    private static String decodeProtoInput(CodedInputStream input, int depth, boolean singleLine) throws IOException {
        StringBuilder s = new StringBuilder("{");
        boolean foundFields = false;
        while (true) {
            final int tag = input.readTag();
            int type = WireFormat.getTagWireType(tag);
            if (tag == 0 || type == WireFormat.WIRETYPE_END_GROUP) {
                break;
            }
            foundFields = true;

            final int number = WireFormat.getTagFieldNumber(tag);
            s.append(number).append(":");

            switch (type) {
                case WireFormat.WIRETYPE_VARINT:
                    s.append(input.readInt64());
                    break;
                case WireFormat.WIRETYPE_FIXED64:
                    s.append(Double.longBitsToDouble(input.readFixed64()));
                    break;
                case WireFormat.WIRETYPE_LENGTH_DELIMITED:
                    ByteString data = input.readBytes();
                    try {
                        String submessage = decodeProto(data, depth + 1, singleLine);
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
                                s.append("\"").append(str).append("\"");
                            }
                        }
                        s.append(submessage);
                    } catch (IOException e) {
                        s.append('"').append(new String(data.toByteArray())).append('"');
                    }
                    break;
                case WireFormat.WIRETYPE_START_GROUP:
                    s.append(decodeProtoInput(input, depth + 1, singleLine));
                    break;
                case WireFormat.WIRETYPE_FIXED32:
                    s.append(Float.intBitsToFloat(input.readFixed32()));
                    break;
                default:
                    throw new InvalidProtocolBufferException("Invalid wire type");
            }
            protoNewline(depth, s, singleLine);
        }
        return s.deleteCharAt(s.lastIndexOf(",")).append('}').toString();
    }

    private static void protoNewline(int depth, StringBuilder s, boolean noNewline) {
        if (noNewline) {
            s.append(",");
            return;
        }
        s.append('\n');
        for (int i = 0; i <= depth; i++) {
            s.append(":");
        }
    }


}
