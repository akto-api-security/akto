package com.akto.util.modifier;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public class GraphQLPayloadModifier extends PayloadFormatModifier {

    String operationName;
    String defName;

    public GraphQLPayloadModifier(String operationName, String defName) {
        this.operationName = operationName;
        this.defName = defName;
    }
    
    @Override
    public String toJSON(String orig) {
        // BasicDBObject query = extractQuery(orig);
        
        return null;
    }   

    private BasicDBObject extractQuery(String orig) {
        // orig = orig.trim();
        
        // if (orig.startsWith("{")) {
        //     return BasicDBObject.parse(orig);
        // } else if (orig.startsWith("[")) {
        //     orig = "{\"json\": " + orig + "}";
        //     Object list = BasicDBObject.parse(orig).get("json");
        //     for(Object ll: (BasicDBList) list) {
        //         BasicDBObject entry = (BasicDBObject) (ll);

                
                
        //     }
        // } else {
        //     throw new IllegalArgumentException("Invalid json: " + orig);
        // }

        return null;
    }

    @Override
    public String fromJSON(String orig) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'fromJSON'");
    }


    
}
