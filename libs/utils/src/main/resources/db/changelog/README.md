# Akto Liquibase MongoDB Migrations

This directory contains Liquibase changelogs for MongoDB/Cosmos DB migrations.

Runtime entry point:

- `common.xml` runs once against the `common` database.
- `billing.xml` runs once against the `billing` database.
- `account.xml` runs once per account database.

Each root changelog includes its matching `baseline-*.xml` file and then includes all migration files from its scope folder:

- `common/*.xml`
- `billing/*.xml`
- `account/*.xml`

`errorIfMissingOrEmpty="true"` is enabled for all three scope folders, so every folder must contain at least one XML migration file.

## When To Add A Migration

Add a migration when a code change needs a database shape change, for example:

- New collection.
- New, changed, or removed index.
- Seed data required by application code.
- Data backfill or field rename.
- Any DAO/init change that customer environments must receive during upgrade.

Do not edit an already-merged changeset after it has run anywhere. Liquibase stores checksums and will fail if applied changesets are modified.

## File Naming

Use ordered names so `includeAll` applies files predictably:

```text
common/002-add-feature-flags-index.xml
billing/002-seed-usage-metric-info.xml
account/002-backfill-api-collection-field.xml
```

Use unique changeset IDs across the repo. A good pattern is:

```text
<number>-<scope>-<short-purpose>
```

Example:

```xml
<changeSet id="002-account-add-api-collection-index" author="akto">
    ...
</changeSet>
```

## XML Template

Use the same `mongodb:` namespace style as the baseline files:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:mongodb="http://www.liquibase.org/xml/ns/mongodb"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd
        http://www.liquibase.org/xml/ns/mongodb
        http://www.liquibase.org/xml/ns/mongodb/liquibase-mongodb-latest.xsd">

    <changeSet id="002-account-example" author="akto">
        <!-- migration goes here -->
    </changeSet>

</databaseChangeLog>
```

## MongoDB Change Types

Liquibase MongoDB supports these MongoDB-specific change types.

### createCollection

Creates a collection.

```xml
<changeSet id="002-account-create-example-events" author="akto">
    <mongodb:createCollection collectionName="example_events"/>
    <rollback>
        <mongodb:dropCollection collectionName="example_events"/>
    </rollback>
</changeSet>
```

### dropCollection

Drops a collection. Use carefully. Prefer a rollback and a precondition when possible.

```xml
<changeSet id="003-account-drop-old-events" author="akto">
    <mongodb:dropCollection collectionName="old_events"/>
    <rollback>
        <mongodb:createCollection collectionName="old_events"/>
    </rollback>
</changeSet>
```

### createIndex

Creates an index. `keys` and `options` are MongoDB JSON documents.

```xml
<changeSet id="004-account-create-events-index" author="akto">
    <mongodb:createIndex collectionName="example_events">
        <mongodb:keys>{ "accountId": 1, "createdAt": -1 }</mongodb:keys>
        <mongodb:options>{ "name": "accountId_1_createdAt_-1" }</mongodb:options>
    </mongodb:createIndex>
    <rollback>
        <mongodb:dropIndex collectionName="example_events">
            <mongodb:keys>{ "accountId": 1, "createdAt": -1 }</mongodb:keys>
        </mongodb:dropIndex>
    </rollback>
</changeSet>
```

### dropIndex

Drops an index. Use the same key document that was used to create the index.

```xml
<changeSet id="005-account-drop-events-index" author="akto">
    <mongodb:dropIndex collectionName="example_events">
        <mongodb:keys>{ "accountId": 1, "createdAt": -1 }</mongodb:keys>
    </mongodb:dropIndex>
</changeSet>
```

### insertOne

Inserts one document.

```xml
<changeSet id="006-common-seed-config" author="akto">
    <mongodb:insertOne collectionName="configs">
        <mongodb:document>{ "_id": "example_config", "enabled": true }</mongodb:document>
    </mongodb:insertOne>
    <rollback>
        <mongodb:runCommand>
            <mongodb:command>{ "delete": "configs", "deletes": [{ "q": { "_id": "example_config" }, "limit": 1 }] }</mongodb:command>
        </mongodb:runCommand>
    </rollback>
</changeSet>
```

### insertMany

Inserts multiple documents.

```xml
<changeSet id="007-billing-seed-metrics" author="akto">
    <mongodb:insertMany collectionName="usage_metric_info">
        <mongodb:documents>
            [
              { "_id": "metric_a", "name": "Metric A" },
              { "_id": "metric_b", "name": "Metric B" }
            ]
        </mongodb:documents>
    </mongodb:insertMany>
</changeSet>
```

### runCommand

Runs a MongoDB command against the current database. Use this for updates, deletes, backfills, renames, aggregation updates, and commands not covered by a dedicated change type.

```xml
<changeSet id="008-account-backfill-example-field" author="akto">
    <mongodb:runCommand>
        <mongodb:command>{ "update": "api_collections", "updates": [{ "q": { "archived": { "$exists": false } }, "u": { "$set": { "archived": false } }, "multi": true }] }</mongodb:command>
    </mongodb:runCommand>
</changeSet>
```

### adminCommand

Runs an admin command. Use only when the command truly belongs on the admin database.

```xml
<changeSet id="009-common-admin-command-example" author="akto">
    <mongodb:adminCommand>
        <mongodb:command>{ "ping": 1 }</mongodb:command>
    </mongodb:adminCommand>
</changeSet>
```

## Useful Core Liquibase Elements

These are not MongoDB-specific, but are useful in MongoDB migrations.

### empty

Creates a tracked no-op changeset. The `001-init.xml` files use this so strict `includeAll` has a real file in each scope.

```xml
<changeSet id="001-init-common" author="akto">
    <empty/>
</changeSet>
```

### preConditions

Use preconditions to make migrations safer and idempotent. If a precondition fails, `MARK_RAN` can record the changeset without applying it.

```xml
<changeSet id="010-account-create-index-if-collection-exists" author="akto">
    <preConditions onFail="MARK_RAN">
        <mongodb:collectionExists collectionName="example_events"/>
    </preConditions>
    <mongodb:createIndex collectionName="example_events">
        <mongodb:keys>{ "createdAt": -1 }</mongodb:keys>
        <mongodb:options>{ "name": "createdAt_-1" }</mongodb:options>
    </mongodb:createIndex>
</changeSet>
```

### rollback

Add rollback blocks for reversible changes. For data backfills, rollback may be impossible; in that case, document why in an XML comment.

```xml
<rollback>
    <mongodb:dropIndex collectionName="example_events">
        <mongodb:keys>{ "createdAt": -1 }</mongodb:keys>
    </mongodb:dropIndex>
</rollback>
```

## Scope Guide

Use `common/` for shared global data such as users, accounts, setup, common configs, jobs, and global indexes.

Use `billing/` for billing and organization usage data.

Use `account/` for tenant/account databases. These migrations run once per active account, or once against account `1000000` in on-prem mode.

## References

- Liquibase MongoDB tutorial: https://contribute.liquibase.com/extensions-integrations/directory/database-tutorials/mongodb/
- Quarkus Liquibase MongoDB guide: https://quarkus.io/guides/liquibase-mongodb
