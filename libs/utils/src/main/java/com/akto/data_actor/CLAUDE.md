# data_actor/

This package is the data access abstraction layer. All modules (mini-runtime, threat-detection, etc.) talk to the database through `DataActor` — never directly via DAOs.

## The 4-file pattern

Every new database operation touches exactly these files in order:

```
DataActor.java      → abstract method declaration
DbActor.java        → direct DB implementation (delegates to DbLayer)
DbLayer.java        → DAO call
ClientActor.java    → HTTP client implementation (calls database-abstractor)
```

`DataActorFactory` returns a `DbActor` instance when running with direct DB access, or a `ClientActor` instance when running in a module (mini-runtime, threat-detection) that talks to the database-abstractor service.

---

## Adding a new read API

### 1. DataActor.java — abstract method

```java
public abstract List<MyDto> fetchMyDtos();
```

Conventions:
- Prefix: `fetch` for reads, no prefix convention for writes
- Return `List<T>` for collections, `T` for single objects
- No checked exceptions

---

### 2. DbLayer.java — DAO call

```java
public static List<MyDto> fetchMyDtos() {
    return MyDtoDao.instance.findAll(new BasicDBObject());
}
```

Add explicit import for the DTO if `com.akto.dto.*` wildcard is not present.

---

### 3. DbActor.java — delegates to DbLayer

```java
@Override
public List<MyDto> fetchMyDtos() {
    return DbLayer.fetchMyDtos();
}
```

---

### 4. ClientActor.java — HTTP call to database-abstractor

For a **parameterless GET**:
```java
@Override
public List<MyDto> fetchMyDtos() {
    List<MyDto> myDtos = new ArrayList<>();
    Map<String, List<String>> headers = buildHeaders();
    OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchMyDtos", "", "GET", null, headers, "");
    try {
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
        String responsePayload = response.getBody();
        if (response.getStatusCode() != 200 || responsePayload == null) {
            loggerMaker.errorAndAddToDb(null, "invalid response in fetchMyDtos", LoggerMaker.LogDb.RUNTIME);
            return myDtos;
        }
        try {
            BasicDBObject payloadObj = BasicDBObject.parse(responsePayload);
            BasicDBList objList = (BasicDBList) payloadObj.get("myDtosList");
            for (Object obj : objList) {
                BasicDBObject obj2 = (BasicDBObject) obj;
                myDtos.add(objectMapper.readValue(obj2.toJson(), MyDto.class));
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error extracting response in fetchMyDtos", LoggerMaker.LogDb.RUNTIME);
        }
    } catch (Exception e) {
        loggerMaker.errorAndAddToDb(e, "error in fetchMyDtos", LoggerMaker.LogDb.RUNTIME);
    }
    return myDtos;
}
```

For a **POST with parameters**, replace the request line with:
```java
BasicDBObject obj = new BasicDBObject();
obj.put("myParam", myParam);
OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchMyDtos", "", "POST", obj.toString(), headers, "");
```

Response field name convention: use the same name as the getter/setter field in `DbAction` (e.g., `myDtosList`).

---

## The 2-branch split

The database-abstractor service lives on **`feature/cyborg-release`**. Changes to it go on a separate branch cut from there.

| Branch | Files |
|--------|-------|
| New branch from `feature/cyborg-release` | `apps/database-abstractor/src/main/java/com/akto/action/DbAction.java` + `struts.xml` |
| Current feature branch | `DataActor`, `DbActor`, `DbLayer`, `ClientActor` |

---

## database-abstractor: DbAction.java

Add method + field + getter/setter:

```java
// Field (add near other DTO list fields, ~line 140)
List<MyDto> myDtosList;

// Action method
public String fetchMyDtos() {
    try {
        myDtosList = MyDtoDao.instance.findAll(new BasicDBObject());
    } catch (Exception e) {
        loggerMaker.errorAndAddToDb(e, "error in fetchMyDtos: " + e.getMessage());
        return Action.ERROR.toUpperCase();
    }
    return Action.SUCCESS.toUpperCase();
}

// Getter/setter (add near end of file with other getters)
public List<MyDto> getMyDtosList() { return myDtosList; }
public void setMyDtosList(List<MyDto> myDtosList) { this.myDtosList = myDtosList; }
```

---

## database-abstractor: struts.xml

For a **read endpoint** (returns data):
```xml
<action name="api/fetchMyDtos" class="com.akto.action.DbAction" method="fetchMyDtos">
    <interceptor-ref name="json"/>
    <interceptor-ref name="defaultStack" />
    <result name="SUCCESS" type="json"/>
    <result name="ERROR" type="json">
        <param name="statusCode">422</param>
        <param name="ignoreHierarchy">false</param>
        <param name="includeProperties">^actionErrors.*</param>
    </result>
</action>
```

For a **write endpoint** (returns nothing):
```xml
<result name="SUCCESS" type="json">
    <param name="excludeProperties">.*</param>
</result>
```

Add the new action adjacent to related actions (e.g., near other actions for the same DTO).
