# database-abstractor

## Branch
Main branch: `feature/cyborg-release`

## Overview
Java web app (Struts2 + JSON) that acts as DB abstraction layer. Other services call it via JWT-authenticated HTTP APIs.

## Project Structure

root directory: apps/database-abstractor

```
src/main/java/com/akto/
├── action/          # API handlers (Struts2 Action classes)
├── filter/          # Servlet filters (auth, tools auth)
├── merging/         # URL merging logic + cron
├── trafficFilter/   # Traffic filtering logic
├── utils/           # Crons, alerts, Kafka utils
└── FastDiscoveryKafkaConsumer.java

src/main/resources/
└── struts.xml       # API route mappings (action name → class + method)
```

## API Layer

- Route mappings: [struts.xml](src/main/resources/struts.xml)
- All routes follow identical XML pattern: `json` interceptor + `defaultStack` + SUCCESS/ERROR results
- ERROR result: HTTP 422, returns `actionErrors`

### Action Classes

| File | Responsibility |
|------|---------------|
| `DbAction.java` | Main handler — bulk of DB read/write operations |
| `SampleDataAction.java` | Sample traffic data ops |
| `LLMAction.java` | LLM-related operations |
| `CodeAnalysisAction.java` | Code analysis ops |
| `GuardrailPoliciesAction.java` | Guardrail policy ops |
| `BrowserExtensionConfigAction.java` | Browser extension config |
| `JobAction.java` | Job management |
| `AccountJobAction.java` | Account-level job ops |
| `ToolsAction.java` | Tools ops (uses `ToolsAuthFilter`) |
| `InfraMetricsAction.java` | Health check endpoint |

## Authentication

- Filter: [AuthFilter.java](src/main/java/com/akto/filter/AuthFilter.java)
- JWT token in `Authorization` header
- Validates via `JwtAuthenticator.authenticate()`, sets `Context.accountId` from claims
- Exception: `updateModuleInfo` APIs skip auth (even with expired token, extracts accountId from expired claims)
- Tools APIs use separate `ToolsAuthFilter`

## DB Operations

- Action classes call `DbLayer` (from `com.akto.data_actor`) for all DB operations
- No direct DAO calls from action classes — go through `DbLayer`

## Adding a New API

1. Add method to appropriate Action class (or `DbAction` for general DB ops)
2. Add route to `struts.xml` following existing XML pattern
3. DB ops go through `DbLayer`, not DAOs directly

## Adding a New DAO + DTO

1. Create DTO in `libs/dao/src/main/java/com/akto/dto/`
2. Create DAO in `libs/dao/src/main/java/com/akto/dao/`
3. Register in `DaoInit.createCodecRegistry()` (`libs/dao/src/main/java/com/akto/DaoInit.java`):
   - Build `ClassModel` for the DTO: `ClassModel.builder(MyDto.class).enableDiscriminator(true).build()`
   - Add `ClassModel` to `PojoCodecProvider.builder().register(...)` list
   - If DTO has enum fields, add `new EnumCodec<>(MyEnum.class)` to `customEnumCodecs`

## Tests

```
src/test/java/com/akto/
├── action/          # Action class tests
├── merging/         # Merging logic tests
├── trafficFilter/   # Traffic filter tests
└── utils/           # Cron tests
```
