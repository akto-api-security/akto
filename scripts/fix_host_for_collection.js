// Usage:
// mongosh <db> --eval "var collectionId=<id>; var hostValue='<hostname>';" /fix_host_for_collection.js
// Example:
// mongosh 1000000 --eval "var collectionId=1265755654; var hostValue='1726615470-guardrails.akto.io';" /fix_host_for_collection.js

if (typeof collectionId === 'undefined' || typeof hostValue === 'undefined') {
    print("ERROR: collectionId and hostValue are required.");
    print("Usage: mongosh <db> --eval \"var collectionId=<id>; var hostValue='<hostname>';\" /fix_host_for_collection.js");
    quit(1);
}

const aktoId = parseInt(collectionId);

print(`\nCollection ID : ${aktoId}`);
print(`Host value    : ${hostValue}\n`);

const now = Math.floor(Date.now() / 1000);

// If the URL is path-only (e.g. /api/test), prepend https://host so that
// new URL(url).hostname can parse the hostname in the UI.
function toFullUrl(rawUrl, host) {
    if (/^https?:\/\//.test(rawUrl)) return rawUrl;
    return `https://${host}${rawUrl.startsWith('/') ? rawUrl : '/' + rawUrl}`;
}

// ── Fix single_type_info ──────────────────────────────────────────────────────
print("── Updating single_type_info ──");

const endpoints = db.single_type_info.aggregate([
    { $match: { apiCollectionId: aktoId } },
    { $group: { _id: { url: "$url", method: "$method" } } }
]).toArray();

if (endpoints.length === 0) {
    print(`  No single_type_info entries found for collection ${aktoId}`);
} else {
    let inserted = 0, skipped = 0;
    for (const ep of endpoints) {
        const rawUrl = ep._id.url;
        const method = ep._id.method;
        const url = toFullUrl(rawUrl, hostValue);

        // If rawUrl was path-only, update all STI entries for that URL to use
        // the full URL so the path-only version doesn't appear as a duplicate endpoint.
        if (rawUrl !== url) {
            db.single_type_info.updateMany(
                { url: rawUrl, method: method, apiCollectionId: aktoId },
                { $set: { url: url } }
            );
        }

        const result = db.single_type_info.updateOne(
            {
                url: url,
                method: method,
                responseCode: -1,
                isHeader: true,
                param: "host",
                subType: "GENERIC",
                apiCollectionId: aktoId
            },
            {
                $setOnInsert: {
                    url: url,
                    method: method,
                    responseCode: -1,
                    isHeader: true,
                    param: "host",
                    subType: "GENERIC",
                    apiCollectionId: aktoId,
                    collectionIds: [aktoId],
                    count: 1,
                    timestamp: now,
                    lastSeen: now,
                    minValue: 9223372036854675456,
                    maxValue: -9223372036854675456
                }
            },
            { upsert: true }
        );

        if (result.upsertedCount > 0) {
            const note = rawUrl !== url ? ` (normalized from ${rawUrl})` : '';
            print(`  INSERTED → ${method} ${url}${note}`);
            inserted++;
        } else {
            print(`  SKIPPED  → ${method} ${url} (host already exists)`);
            skipped++;
        }
    }
    print(`\n  Total: ${inserted} inserted, ${skipped} skipped`);
}

// ── Fix sample_data ───────────────────────────────────────────────────────────
// sample_data._id is immutable, so path-only URL docs are deleted and re-inserted
// with a full URL _id. The host header is also added to requestHeaders in the same pass.
print("\n── Updating sample_data ──");

const sampleDocs = db.sample_data.find({ "_id.apiCollectionId": aktoId }).toArray();

if (sampleDocs.length === 0) {
    print(`  No sample_data entries found for collection ${aktoId}`);
} else {
    let updated = 0, skipped = 0, failed = 0;
    for (const doc of sampleDocs) {
        const rawUrl = doc._id.url;
        const fullUrl = toFullUrl(rawUrl, hostValue);
        const urlChanged = rawUrl !== fullUrl;

        // Update requestHeaders in all samples to add host if missing
        let headersModified = false;
        const updatedSamples = (doc.samples || []).map(sampleStr => {
            try {
                const sample = JSON.parse(sampleStr);
                const requestHeaders = JSON.parse(sample.requestHeaders || "{}");
                if (!Object.keys(requestHeaders).map(k => k.toLowerCase()).includes("host")) {
                    requestHeaders["host"] = hostValue;
                    sample.requestHeaders = JSON.stringify(requestHeaders);
                    headersModified = true;
                }
                return JSON.stringify(sample);
            } catch (e) {
                return sampleStr;
            }
        });

        if (!urlChanged && !headersModified) {
            print(`  SKIPPED  → ${doc._id.method} ${rawUrl} (already correct)`);
            skipped++;
            continue;
        }

        if (urlChanged) {
            // _id must change: delete old doc and re-insert with full URL
            const newId = {
                apiCollectionId: doc._id.apiCollectionId,
                url: fullUrl,
                method: doc._id.method,
                responseCode: doc._id.responseCode,
                bucketStartEpoch: doc._id.bucketStartEpoch,
                bucketEndEpoch: doc._id.bucketEndEpoch
            };
            const newDoc = Object.assign({}, doc, { _id: newId, samples: updatedSamples });
            try {
                db.sample_data.insertOne(newDoc);
                db.sample_data.deleteOne({ _id: doc._id });
                print(`  MIGRATED → ${doc._id.method} ${rawUrl} → ${fullUrl}`);
                updated++;
            } catch (e) {
                if (e.code === 11000) {
                    // Full-URL doc already exists; just remove the stale path-only doc
                    db.sample_data.deleteOne({ _id: doc._id });
                    print(`  REMOVED  → ${doc._id.method} ${rawUrl} (full URL doc already present)`);
                    updated++;
                } else {
                    print(`  FAILED   → ${doc._id.method} ${rawUrl}: ${e.message}`);
                    failed++;
                }
            }
        } else {
            // _id is already a full URL; only requestHeaders changed
            db.sample_data.updateOne(
                { _id: doc._id },
                { $set: { samples: updatedSamples } }
            );
            print(`  UPDATED  → ${doc._id.method} ${rawUrl} (host header added)`);
            updated++;
        }
    }
    print(`\n  Total: ${updated} updated, ${skipped} skipped, ${failed} failed`);
}

print("\nDone.");
