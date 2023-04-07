package com.akto.util.modifier;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.visitors.JSONVisitor;
import com.mongodb.BasicDBObject;

public class FormPayloadModifier extends PayloadFormatModifier {

    @Override
    public String toJSON(String orig) {
        return HttpRequestResponseUtils.convertFormUrlEncodedToJson(orig);
    }

    @Override
    public String fromJSON(String orig) {
        BasicDBObject json = BasicDBObject.parse(orig);
        JSONVisitor jsonVisitor = new JSONVisitor() {

            String ret = "";
            String lastKey = null;
            @Override
            public void key(String key) {
                this.lastKey = key;
            }

            @Override
            public void vObj() {
                if (this.lastKey != null) {
                    throw new IllegalArgumentException("Found nested json: " + orig);
                }
            }

            @Override
            public void vList() {
                throw new IllegalArgumentException("Found list in json: " + orig);
            }

            @Override
            public void vVal(Object val) {
                if (!this.ret.isEmpty()) {
                    this.ret += "&";
                }
                    
                try {
                    this.ret += this.lastKey + "=" + URLEncoder.encode(val.toString(), StandardCharsets.UTF_8.toString());
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalArgumentException("Error while encoding: " + val);
                }
            }

            @Override
            public String toString() {
                return this.ret;
            }
            
        };

        jsonVisitor.visitObj(json);
        return jsonVisitor.toString();
    }
}
