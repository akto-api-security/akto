package com.akto.oas.requests;

import java.net.URI;
import java.net.URLEncoder;

import org.apache.http.client.methods.RequestBuilder;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Function;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;

public class BxB {

    public static final String X_AKTO_SAMPLE = "x-akto-sample";
    public static final String NO_SAMPLE = "No sample present";
    public static final String NO_PARAMETER = "No parameter present";

    Function<RequestBuilder, RequestBuilder> func;

    public BxB(Function<RequestBuilder, RequestBuilder> func) {
        this.func = func;
    }

    public RequestBuilder apply(RequestBuilder baseGetBuilder) {
        if (this instanceof Err) {
            throw new IllegalStateException(((Err)this).message);
        }
        return func.apply(baseGetBuilder);
    }

    public static BxB compose(BxB thisFunctor, BxB thatFunctor) {

        if (thisFunctor instanceof Err) {
            return thisFunctor;
        } else if (thatFunctor instanceof Err) {
            return thatFunctor;
        }

        return new BxB(new Function<RequestBuilder,RequestBuilder>(){

            @Override
            public RequestBuilder apply(RequestBuilder t) {
                return thisFunctor.apply(thatFunctor.apply(t));
            }
            
        });
    }

    public List<BxB> toList() {
        ArrayList<BxB> ret = new ArrayList<>();
        ret.add(this);
        return ret;
    }

    public static BxB createIdentity() {
        return new BxB(new Function<RequestBuilder,RequestBuilder>(){

            @Override
            public RequestBuilder apply(RequestBuilder t) {
                return RequestBuilder.copy(t.build());
            }
        });
    }

    public static BxB createURIBxB(URI uri) {
        return new BxB(new Function<RequestBuilder,RequestBuilder>(){

            @Override
            public RequestBuilder apply(RequestBuilder t) {
                return t.setUri(uri);
            }
            
        });
    }
        
    public static List<BxB> createHeaderFunctors(Map<String, Parameter> headerParamsMap, String context) {
        if (headerParamsMap == null || headerParamsMap.isEmpty()) {
            return BxB.createIdentity().toList();
        }

        Map<String, String[]> headerValuesMap = new HashMap<>();

        for(Map.Entry<String,Parameter> entry: headerParamsMap.entrySet()) {
            String[] headerValues = extractStr(entry.getValue().getSchema());
            if (headerValues == null || headerValues.length == 0) {
                return BxB.createErr(NO_SAMPLE, context).toList();
            } else {
                headerValuesMap.put(entry.getKey(), headerValues);
            }
        }

        List<BxB> ret = new ArrayList<>();

        for (int i = 0; i < headerValuesMap.values().iterator().next().length; i ++) {
            final int x = i;
            ret.add(new BxB(new Function<RequestBuilder,RequestBuilder>(){

                @Override
                public RequestBuilder apply(RequestBuilder orig) {
                    RequestBuilder t = RequestBuilder.copy(orig.build());
                    for(Map.Entry<String, Parameter> entry: headerParamsMap.entrySet()) {
                        String headerValue = headerValuesMap.get(entry.getKey())[x];
                        t.addHeader(entry.getKey(), headerValue);
                    }

                    return t;
                }
                
            }));
        }

        return ret;
    }

    public static List<BxB> createQueryFunctors(Map<String, Parameter> queryParamsMap, String context) {
        if (queryParamsMap == null || queryParamsMap.isEmpty()) 
            return BxB.createIdentity().toList();

        Map<String, String[]> queryValuesMap = new HashMap<>();    

        for(Map.Entry<String, Parameter> entry: queryParamsMap.entrySet()) {
            String[] queryValues = extractStr(entry.getValue().getSchema());

            if(queryValues == null || queryValues.length == 0) {
                return BxB.createErr(NO_SAMPLE, context).toList();
            } else {
                queryValuesMap.put(entry.getKey(), queryValues);
            }
        }

        List<BxB> ret = new ArrayList<>();

        for (int i = 0; i < queryValuesMap.values().iterator().next().length; i ++) {
            final int x = i;
            ret.add(new BxB(new Function<RequestBuilder,RequestBuilder>(){
                @Override
                public RequestBuilder apply(RequestBuilder orig) {
                    RequestBuilder t = RequestBuilder.copy(orig.build());
                    URI uriOrig = t.getUri();
                    try {
                        URIBuilder uriBuilder = new URIBuilder(uriOrig);
                        for(Map.Entry<String, Parameter>  entry: queryParamsMap.entrySet()) {
                            uriBuilder.addParameter(entry.getKey(), queryValuesMap.get(entry.getKey())[x]);
                        }

                        return t.setUri(uriBuilder.build());    
                    } catch (Exception e) {
                        throw new IllegalStateException("invalid URI " + uriOrig);
                    }
                }
            }));  
        }

        return ret;
    }

    public static BxB createCookie(Map<String, Parameter> cookieParamsMap, String pathStr, String method) {
        if (cookieParamsMap != null && !cookieParamsMap.isEmpty()) {
            return BxB.createErr("Cookies not supported", method + " " + pathStr + " > cookies");
        } else {
            return BxB.createIdentity();
        }
    }

    public static BxB createGet() {
        return new BxB(new Function<RequestBuilder,RequestBuilder>(){

            @Override
            public RequestBuilder apply(RequestBuilder orig) {
                return RequestBuilder.get();
            }
            
        });
    }

    public static BxB createDelete() {
        return new BxB(new Function<RequestBuilder,RequestBuilder>(){

            @Override
            public RequestBuilder apply(RequestBuilder orig) {
                return RequestBuilder.delete();
            }
            
        });
    }

    public static BxB createPut(String body) {
        return new BxB(new Function<RequestBuilder,RequestBuilder>(){

            @Override
            public RequestBuilder apply(RequestBuilder orig) {
                return RequestBuilder.put().setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            }
            
        });
    }

    public static BxB createPost(String body) {
        return new BxB(new Function<RequestBuilder,RequestBuilder>(){

            @Override
            public RequestBuilder apply(RequestBuilder orig) {
                return RequestBuilder.post().setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            }
            
        });
    }

    public static String[] extractStr(Schema schema) {
        Map<String, Object> extensions = schema.getExtensions();
        String script1 = URLEncoder.encode("><script>alert(1);</script>\"@example.org");
        String script2 = URLEncoder.encode("user@[IPv6:2001:db8::1]");
        String script3 = URLEncoder.encode("1=1--");
        String[] ret = new String[]{"1", script1, script2, script3};
        if (extensions == null || extensions.get(X_AKTO_SAMPLE) == null)
            return ret;

        return new String[]{extensions.get(X_AKTO_SAMPLE).toString(), script1, script2, script3};
    }

    public static String[] extractJSON(Schema schema) {
        Map<String, Object> extensions = schema.getExtensions();

        if (extensions != null && extensions.get(X_AKTO_SAMPLE) != null) {
            return new String[] {Json.pretty(extensions.get(X_AKTO_SAMPLE))};            
        }


        switch (schema.getType().toLowerCase()) {
            case "object": {
                if (schema.getProperties() == null) {
                    return null;
                }
                
                BasicDBObject dbObject = new BasicDBObject();

                for (Map.Entry<String, Schema>  entry: ((Map<String, Schema>) (schema.getProperties())).entrySet()) {
                    dbObject.put(entry.getKey(), entry.getValue().getExample().toString());
                }
                return new String[] {dbObject.toString(), new BasicDBObject("admin", "1").toJson(), new BasicDBObject("1=1--", "1=1--").toString()};
            }

            case "array": {
                if (!(schema instanceof ArraySchema)) {
                    return null;
                }

                ArraySchema arraySchema = (ArraySchema) schema;
                BasicDBList dList = new BasicDBList();

                if (arraySchema.getItems() == null) {
                    return null;
                }

                Map<String, Schema> itemObjProps = arraySchema.getItems().getProperties();
                BasicDBObject dbObject = new BasicDBObject();

                for (Map.Entry<String, Schema>  entry: itemObjProps.entrySet()) {
                    dbObject.put(entry.getKey(), entry.getValue().getExample().toString());
                }

                dList.add(dbObject);
                return new String[] {dList.toString(), new BasicDBObject("admin", "1").toJson(), new BasicDBObject("1=1--", "1=1--").toString()};
            }

            default:
                
                return null;
        }



    }

    public static BxB createErr(String message, String context) {
        return new Err(context + ": " + message);
    }

    public static class Err extends BxB {
        public final String message;

        public Err(String message) {
            super(null);
            this.message = message;
        }

        public String toString() {
            return this.message;
        }
    }
}
