package com.akto.postman;

import com.akto.ApiRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;


public class Main {
    private final String apiKey;
    public static final String BASE_URL = "https://api.getpostman.com/";

    public Main(String apiKey) {
        this.apiKey = apiKey;
    }

    public static void main(String[] args) {
        Main main = new Main("PMAK-61dda7b35e30ce28fc2a131e-ccf78be950e3026bec27313d4472b7259b");
//        main.fetchWorkspaces();
        String workspaceId = "d0510f32-2e14-4f87-8024-b85bd85de3f1";
        String collectionId = "223645de-9ede-4f12-93c4-1dcf80487ee6";
//        main.createApiWithSchema();
//        String apiId = main.createApi(workspaceId,"sixth API");
//        System.out.println(apiId);
    }

    public void getPostmanCollection(String collectionId) {
        String url = "https://api.getpostman.com/collections/" + "";
        JsonNode jsonNode = ApiRequest.getRequest(generateHeadersWithAuth(), url);
        jsonNode.get("collection");

    }

    public String createApi(String workspaceId, String apiName) {
        String url = BASE_URL + "apis?workspace="+workspaceId;

        JSONObject child = new JSONObject();
        child.put("name", apiName);
        child.put("summary", "Summary");
        child.put("description", "description");

        JSONObject requestBody = new JSONObject();
        requestBody.put("api", child);

        String json = requestBody.toString();
        JsonNode node = ApiRequest.postRequest(generateHeadersWithAuth(), url,json);
        String apiId = node.get("api").get("id").textValue();
        return apiId;
    }

    public String getVersion(String apiId) {
        String url = BASE_URL + "apis/"+apiId+"/versions";
        JsonNode node = ApiRequest.getRequest(generateHeadersWithAuth(), url);
        String version = node.get("versions").get(0).get("id").textValue();
        return version;
    }

    public void addSchema(String apiId, String version, String openApiSchema) {
        String url = BASE_URL + "apis/"+apiId+"/versions/" + version + "/schemas";

        JSONObject child = new JSONObject();
        child.put("language", "json");
        child.put("schema", openApiSchema);
        child.put("type", "openapi3");

        JSONObject requestBody = new JSONObject();
        requestBody.put("schema", child);

        String json = requestBody.toString();

//        String json = "{\"schema\": { \"language\": \"json\", \"schema\": \"{   \\\"openapi\\\" : \\\"3.0.1\\\",   \\\"info\\\" : {     \\\"title\\\" : \\\"Default\\\",     \\\"description\\\" : \\\"Akto generated openAPI file\\\",     \\\"version\\\" : \\\"1.0.0\\\"   },   \\\"paths\\\" : {     \\\"/v2/pet\\\" : {       \\\"description\\\" : \\\"description\\\",       \\\"put\\\" : {         \\\"summary\\\" : \\\"PUT request for endpoint https://petstore.swagger.io/v2/pet\\\",         \\\"operationId\\\" : \\\"https://petstore.swagger.io/v2/pet-PUT\\\",         \\\"requestBody\\\" : {           \\\"content\\\" : {             \\\"application/json\\\" : {               \\\"schema\\\" : {                 \\\"type\\\" : \\\"object\\\",                 \\\"properties\\\" : {                   \\\"photoUrls\\\" : {                     \\\"type\\\" : \\\"array\\\",                     \\\"items\\\" : {                       \\\"type\\\" : \\\"string\\\"                     }                   },                   \\\"name\\\" : {                     \\\"type\\\" : \\\"string\\\"                   },                   \\\"id\\\" : {                     \\\"type\\\" : \\\"integer\\\",                     \\\"format\\\" : \\\"int32\\\"                   },                   \\\"category\\\" : {                     \\\"type\\\" : \\\"object\\\",                     \\\"properties\\\" : {                       \\\"name\\\" : {                         \\\"type\\\" : \\\"string\\\"                       },                       \\\"id\\\" : {                         \\\"type\\\" : \\\"integer\\\",                         \\\"format\\\" : \\\"int32\\\"                       }                     }                   },                   \\\"tags\\\" : {                     \\\"type\\\" : \\\"array\\\",                     \\\"items\\\" : {                       \\\"type\\\" : \\\"object\\\",                       \\\"properties\\\" : {                         \\\"name\\\" : {                           \\\"type\\\" : \\\"string\\\"                         },                         \\\"id\\\" : {                           \\\"type\\\" : \\\"integer\\\",                           \\\"format\\\" : \\\"int32\\\"                         }                       }                     }                   },                   \\\"status\\\" : {                     \\\"type\\\" : \\\"string\\\"                   }                 },                 \\\"description\\\" : \\\"Sample description\\\"               }             }           }         },         \\\"responses\\\" : {           \\\"200\\\" : {             \\\"description\\\" : \\\"description\\\",             \\\"content\\\" : {               \\\"application/json\\\" : {                 \\\"schema\\\" : {                   \\\"type\\\" : \\\"object\\\",                   \\\"properties\\\" : {                     \\\"photoUrls\\\" : {                       \\\"type\\\" : \\\"array\\\",                       \\\"items\\\" : {                         \\\"type\\\" : \\\"string\\\"                       }                     },                     \\\"name\\\" : {                       \\\"type\\\" : \\\"string\\\"                     },                     \\\"id\\\" : {                       \\\"type\\\" : \\\"integer\\\",                       \\\"format\\\" : \\\"int32\\\"                     },                     \\\"category\\\" : {                       \\\"type\\\" : \\\"object\\\",                       \\\"properties\\\" : {                         \\\"name\\\" : {                           \\\"type\\\" : \\\"string\\\"                         },                         \\\"id\\\" : {                           \\\"type\\\" : \\\"integer\\\",                           \\\"format\\\" : \\\"int32\\\"                         }                       }                     },                     \\\"tags\\\" : {                       \\\"type\\\" : \\\"array\\\",                       \\\"items\\\" : {                         \\\"type\\\" : \\\"object\\\",                         \\\"properties\\\" : {                           \\\"name\\\" : {                             \\\"type\\\" : \\\"string\\\"                           },                           \\\"id\\\" : {                             \\\"type\\\" : \\\"integer\\\",                             \\\"format\\\" : \\\"int32\\\"                           }                         }                       }                     },                     \\\"status\\\" : {                       \\\"type\\\" : \\\"string\\\"                     }                   },                   \\\"description\\\" : \\\"Sample description\\\"                 }               }             }           }         }       },       \\\"post\\\" : {         \\\"summary\\\" : \\\"POST request for endpoint https://petstore.swagger.io/v2/pet\\\",         \\\"operationId\\\" : \\\"https://petstore.swagger.io/v2/pet-POST\\\",         \\\"requestBody\\\" : {           \\\"content\\\" : {             \\\"application/json\\\" : {               \\\"schema\\\" : {                 \\\"type\\\" : \\\"object\\\",                 \\\"properties\\\" : {                   \\\"photoUrls\\\" : {                     \\\"type\\\" : \\\"array\\\",                     \\\"items\\\" : {                       \\\"type\\\" : \\\"string\\\"                     }                   },                   \\\"name\\\" : {                     \\\"type\\\" : \\\"string\\\"                   },                   \\\"id\\\" : {                     \\\"type\\\" : \\\"integer\\\",                     \\\"format\\\" : \\\"int32\\\"                   },                   \\\"category\\\" : {                     \\\"type\\\" : \\\"object\\\",                     \\\"properties\\\" : {                       \\\"name\\\" : {                         \\\"type\\\" : \\\"string\\\"                       },                       \\\"id\\\" : {                         \\\"type\\\" : \\\"integer\\\",                         \\\"format\\\" : \\\"int32\\\"                       }                     }                   },                   \\\"status\\\" : {                     \\\"type\\\" : \\\"string\\\"                   },                   \\\"tags\\\" : {                     \\\"type\\\" : \\\"array\\\",                     \\\"items\\\" : {                       \\\"type\\\" : \\\"object\\\",                       \\\"properties\\\" : {                         \\\"name\\\" : {                           \\\"type\\\" : \\\"string\\\"                         },                         \\\"id\\\" : {                           \\\"type\\\" : \\\"integer\\\",                           \\\"format\\\" : \\\"int32\\\"                         }                       }                     }                   }                 },                 \\\"description\\\" : \\\"Sample description\\\"               }             }           }         },         \\\"responses\\\" : {           \\\"200\\\" : {             \\\"description\\\" : \\\"description\\\",             \\\"content\\\" : {               \\\"application/json\\\" : {                 \\\"schema\\\" : {                   \\\"type\\\" : \\\"object\\\",                   \\\"properties\\\" : {                     \\\"photoUrls\\\" : {                       \\\"type\\\" : \\\"array\\\",                       \\\"items\\\" : {                         \\\"type\\\" : \\\"string\\\"                       }                     },                     \\\"name\\\" : {                       \\\"type\\\" : \\\"string\\\"                     },                     \\\"id\\\" : {                       \\\"type\\\" : \\\"integer\\\",                       \\\"format\\\" : \\\"int32\\\"                     },                     \\\"category\\\" : {                       \\\"type\\\" : \\\"object\\\",                       \\\"properties\\\" : {                         \\\"name\\\" : {                           \\\"type\\\" : \\\"string\\\"                         },                         \\\"id\\\" : {                           \\\"type\\\" : \\\"integer\\\",                           \\\"format\\\" : \\\"int32\\\"                         }                       }                     },                     \\\"status\\\" : {                       \\\"type\\\" : \\\"string\\\"                     },                     \\\"tags\\\" : {                       \\\"type\\\" : \\\"array\\\",                       \\\"items\\\" : {                         \\\"type\\\" : \\\"object\\\",                         \\\"properties\\\" : {                           \\\"name\\\" : {                             \\\"type\\\" : \\\"string\\\"                           },                           \\\"id\\\" : {                             \\\"type\\\" : \\\"integer\\\",                             \\\"format\\\" : \\\"int32\\\"                           }                         }                       }                     }                   },                   \\\"description\\\" : \\\"Sample description\\\"                 }               }             }           }         }       },       \\\"servers\\\" : [ {         \\\"url\\\" : \\\"https://petstore.swagger.io\\\"       } ],       \\\"parameters\\\" : [ ]     },     \\\"/v2/pet/{param1}\\\" : {       \\\"description\\\" : \\\"description\\\",       \\\"post\\\" : {         \\\"summary\\\" : \\\"POST request for endpoint https://petstore.swagger.io/v2/pet/INTEGER\\\",         \\\"operationId\\\" : \\\"https://petstore.swagger.io/v2/pet/INTEGER-POST\\\",         \\\"requestBody\\\" : {           \\\"content\\\" : {             \\\"application/json\\\" : {               \\\"schema\\\" : {                 \\\"type\\\" : \\\"object\\\",                 \\\"properties\\\" : {                   \\\"name\\\" : {                     \\\"type\\\" : \\\"string\\\"                   },                   \\\"status\\\" : {                     \\\"type\\\" : \\\"string\\\"                   }                 },                 \\\"description\\\" : \\\"Sample description\\\"               }             }           }         },         \\\"responses\\\" : {           \\\"404\\\" : {             \\\"description\\\" : \\\"description\\\",             \\\"content\\\" : {               \\\"application/json\\\" : {                 \\\"schema\\\" : {                   \\\"type\\\" : \\\"object\\\",                   \\\"properties\\\" : {                     \\\"code\\\" : {                       \\\"type\\\" : \\\"integer\\\",                       \\\"format\\\" : \\\"int32\\\"                     },                     \\\"message\\\" : {                       \\\"type\\\" : \\\"string\\\"                     },                     \\\"type\\\" : {                       \\\"type\\\" : \\\"string\\\"                     }                   },                   \\\"description\\\" : \\\"Sample description\\\"                 }               }             }           },           \\\"200\\\" : {             \\\"description\\\" : \\\"description\\\",             \\\"content\\\" : {               \\\"application/json\\\" : {                 \\\"schema\\\" : {                   \\\"type\\\" : \\\"object\\\",                   \\\"properties\\\" : {                     \\\"code\\\" : {                       \\\"type\\\" : \\\"integer\\\",                       \\\"format\\\" : \\\"int32\\\"                     },                     \\\"type\\\" : {                       \\\"type\\\" : \\\"string\\\"                     },                     \\\"message\\\" : {                       \\\"type\\\" : \\\"integer\\\",                       \\\"format\\\" : \\\"int32\\\"                     }                   },                   \\\"description\\\" : \\\"Sample description\\\"                 }               }             }           }         }       },       \\\"servers\\\" : [ {         \\\"url\\\" : \\\"https://petstore.swagger.io\\\"       } ],       \\\"parameters\\\" : [ {         \\\"name\\\" : \\\"param1\\\",         \\\"in\\\" : \\\"path\\\",         \\\"required\\\" : true,         \\\"schema\\\" : {           \\\"type\\\" : \\\"integer\\\",           \\\"format\\\" : \\\"int32\\\"         }       } ]     },     \\\"/v2/store/order\\\" : {       \\\"description\\\" : \\\"description\\\",       \\\"post\\\" : {         \\\"summary\\\" : \\\"POST request for endpoint https://petstore.swagger.io/v2/store/order\\\",         \\\"operationId\\\" : \\\"https://petstore.swagger.io/v2/store/order-POST\\\",         \\\"requestBody\\\" : {           \\\"content\\\" : {             \\\"application/json\\\" : {               \\\"schema\\\" : {                 \\\"type\\\" : \\\"object\\\",                 \\\"properties\\\" : {                   \\\"quantity\\\" : {                     \\\"type\\\" : \\\"integer\\\",                     \\\"format\\\" : \\\"int32\\\"                   },                   \\\"petId\\\" : {                     \\\"type\\\" : \\\"integer\\\",                     \\\"format\\\" : \\\"int32\\\"                   },                   \\\"id\\\" : {                     \\\"type\\\" : \\\"integer\\\",                     \\\"format\\\" : \\\"int32\\\"                   },                   \\\"complete\\\" : {                     \\\"type\\\" : \\\"boolean\\\"                   },                   \\\"shipDate\\\" : {                     \\\"type\\\" : \\\"string\\\"                   },                   \\\"status\\\" : {                     \\\"type\\\" : \\\"string\\\"                   }                 },                 \\\"description\\\" : \\\"Sample description\\\"               }             }           }         },         \\\"responses\\\" : {           \\\"200\\\" : {             \\\"description\\\" : \\\"description\\\",             \\\"content\\\" : {               \\\"application/json\\\" : {                 \\\"schema\\\" : {                   \\\"type\\\" : \\\"object\\\",                   \\\"properties\\\" : {                     \\\"petId\\\" : {                       \\\"type\\\" : \\\"integer\\\",                       \\\"format\\\" : \\\"int32\\\"                     },                     \\\"quantity\\\" : {                       \\\"type\\\" : \\\"integer\\\",                       \\\"format\\\" : \\\"int32\\\"                     },                     \\\"id\\\" : {                       \\\"type\\\" : \\\"integer\\\",                       \\\"format\\\" : \\\"int32\\\"                     },                     \\\"shipDate\\\" : {                       \\\"type\\\" : \\\"string\\\"                     },                     \\\"complete\\\" : {                       \\\"type\\\" : \\\"boolean\\\"                     },                     \\\"status\\\" : {                       \\\"type\\\" : \\\"string\\\"                     }                   },                   \\\"description\\\" : \\\"Sample description\\\"                 }               }             }           }         }       },       \\\"servers\\\" : [ {         \\\"url\\\" : \\\"https://petstore.swagger.io\\\"       } ],       \\\"parameters\\\" : [ ]     },     \\\"/v2/user\\\" : {       \\\"description\\\" : \\\"description\\\",       \\\"post\\\" : {         \\\"summary\\\" : \\\"POST request for endpoint https://petstore.swagger.io/v2/user\\\",         \\\"operationId\\\" : \\\"https://petstore.swagger.io/v2/user-POST\\\",         \\\"requestBody\\\" : {           \\\"content\\\" : {             \\\"application/json\\\" : {               \\\"schema\\\" : {                 \\\"type\\\" : \\\"object\\\",                 \\\"properties\\\" : {                   \\\"lastName\\\" : {                     \\\"type\\\" : \\\"string\\\"                   },                   \\\"firstName\\\" : {                     \\\"type\\\" : \\\"string\\\"                   },                   \\\"password\\\" : {                     \\\"type\\\" : \\\"string\\\"                   },                   \\\"userStatus\\\" : {                     \\\"type\\\" : \\\"integer\\\",                     \\\"format\\\" : \\\"int32\\\"                   },                   \\\"phone\\\" : {                     \\\"type\\\" : \\\"string\\\"                   },                   \\\"id\\\" : {                     \\\"type\\\" : \\\"integer\\\",                     \\\"format\\\" : \\\"int32\\\"                   },                   \\\"email\\\" : {                     \\\"type\\\" : \\\"string\\\"                   },                   \\\"username\\\" : {                     \\\"type\\\" : \\\"string\\\"                   }                 },                 \\\"description\\\" : \\\"Sample description\\\"               }             }           }         },         \\\"responses\\\" : {           \\\"200\\\" : {             \\\"description\\\" : \\\"description\\\",             \\\"content\\\" : {               \\\"application/json\\\" : {                 \\\"schema\\\" : {                   \\\"type\\\" : \\\"object\\\",                   \\\"properties\\\" : {                     \\\"code\\\" : {                       \\\"type\\\" : \\\"integer\\\",                       \\\"format\\\" : \\\"int32\\\"                     },                     \\\"message\\\" : {                       \\\"type\\\" : \\\"integer\\\",                       \\\"format\\\" : \\\"int32\\\"                     },                     \\\"type\\\" : {                       \\\"type\\\" : \\\"string\\\"                     }                   },                   \\\"description\\\" : \\\"Sample description\\\"                 }               }             }           }         }       },       \\\"servers\\\" : [ {         \\\"url\\\" : \\\"https://petstore.swagger.io\\\"       } ],       \\\"parameters\\\" : [ ]     },     \\\"/v2/user/{param1}\\\" : {       \\\"description\\\" : \\\"description\\\",       \\\"post\\\" : {         \\\"summary\\\" : \\\"POST request for endpoint https://petstore.swagger.io/v2/user/STRING\\\",         \\\"operationId\\\" : \\\"https://petstore.swagger.io/v2/user/STRING-POST\\\",         \\\"responses\\\" : {           \\\"200\\\" : {             \\\"description\\\" : \\\"description\\\",             \\\"content\\\" : {               \\\"application/json\\\" : {                 \\\"schema\\\" : {                   \\\"type\\\" : \\\"object\\\",                   \\\"properties\\\" : {                     \\\"code\\\" : {                       \\\"type\\\" : \\\"integer\\\",                       \\\"format\\\" : \\\"int32\\\"                     },                     \\\"message\\\" : {                       \\\"type\\\" : \\\"string\\\"                     },                     \\\"type\\\" : {                       \\\"type\\\" : \\\"string\\\"                     }                   },                   \\\"description\\\" : \\\"Sample description\\\"                 }               }             }           }         }       },       \\\"servers\\\" : [ {         \\\"url\\\" : \\\"https://petstore.swagger.io\\\"       } ],       \\\"parameters\\\" : [ {         \\\"name\\\" : \\\"param1\\\",         \\\"in\\\" : \\\"path\\\",         \\\"required\\\" : true,         \\\"schema\\\" : {           \\\"type\\\" : \\\"string\\\"         }       } ]     }   } } \", \"type\": \"openapi3\"  }}";

        // {"schema": { "language": "json", "schema": "", "type": "openapi3"  }}

        JsonNode node = ApiRequest.postRequest(generateHeadersWithAuth(), url,json);
    }

    public void createApiWithSchema(String workspaceId,String apiName, String openApiSchema) {
        String apiId = createApi(workspaceId,apiName);
        String version = getVersion(apiId);
        addSchema(apiId, version, openApiSchema);

    }


    public JsonNode fetchApiCollections() {
        String url = BASE_URL + "collections";
        JsonNode jsonNode = ApiRequest.getRequest(generateHeadersWithAuth(), url);
        if (jsonNode == null) return null;

        return jsonNode.get("collections");
    }

    public JsonNode fetchPostmanCollectionString(String collectionId) {
        String url = BASE_URL + "collections/" + collectionId;
        JsonNode jsonNode = ApiRequest.getRequest(generateHeadersWithAuth(), url);
        if (jsonNode == null) return null;

        return jsonNode;
    }

    public JsonNode fetchWorkspaces() {
        String url = BASE_URL + "workspaces";
        JsonNode jsonNode = ApiRequest.getRequest(generateHeadersWithAuth(), url);
        if (jsonNode == null) return null;

        return jsonNode.get("workspaces");
    }

    public Map<String,String> generateHeadersWithAuth() {
        Map<String,String> headersMap = new HashMap<>();
        headersMap.put("X-API-Key",apiKey);
        return headersMap;
    }

}
