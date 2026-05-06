// Java String.hashCode() equivalent
function javaHashCode(str) {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
        hash = (Math.imul(31, hash) + str.charCodeAt(i)) | 0;
    }
    return hash < 0 ? -hash : hash;
}

// Extract host from a URL string
function extractHost(url) {
    if (!url) return null;
    const match = url.match(/^https?:\/\/([^\/]+)/);
    return match ? match[1] : null;
}


// Find all postman file/workspace uploads that completed ingestion
const uploads = db.file_uploads.find({
    uploadType: { $in: ["POSTMAN_FILE", "POSTMAN_WORKSPACE"] },
    ingestionComplete: true
}).toArray();

const seenAktoIds = new Set();
const collectionsToFix = [];

for (const upload of uploads) {
    const collectionIds = upload.postmanCollectionIds || {};
    for (const [postmanCollId, collectionName] of Object.entries(collectionIds)) {
        const aktoId = javaHashCode(postmanCollId);

        if (seenAktoIds.has(aktoId)) continue;

        // Sample one log to check host header and extract host from URL
        const sampleLog = db.file_upload_logs.findOne({
            postmanCollectionId: postmanCollId,
            aktoFormat: { $exists: true }
        });

        if (!sampleLog || !sampleLog.aktoFormat) continue;

        let hostHeaderMissing = true;
        let hostValue = null;
        try {
            const aktoFormat = JSON.parse(sampleLog.aktoFormat);
            const requestHeaders = JSON.parse(aktoFormat.requestHeaders || "{}");
            const headerKeys = Object.keys(requestHeaders).map(k => k.toLowerCase());
            hostHeaderMissing = !headerKeys.includes("host");
            if (hostHeaderMissing) {
                hostValue = extractHost(aktoFormat.path);
            }
        } catch (e) {}

        if (hostHeaderMissing && hostValue) {
            seenAktoIds.add(aktoId);
            collectionsToFix.push({ aktoId, collectionName, hostValue, postmanCollId });
        }
    }
}

print(`\nFound ${collectionsToFix.length} collection(s) with missing host header:\n`);
collectionsToFix.forEach(c => print(`  - [${c.aktoId}] ${c.collectionName} → host: ${c.hostValue}`));

// ── Fix single_type_info ─────────────────────────────────────────────────────
print("\n── Updating single_type_info ──");
const now = Math.floor(Date.now() / 1000);

for (const { aktoId, hostValue } of collectionsToFix) {
    // Get all distinct url+method combos for this collection
    const endpoints = db.single_type_info.aggregate([
        { $match: { apiCollectionId: aktoId } },
        { $group: { _id: { url: "$url", method: "$method" } } }
    ]).toArray();

    let upserted = 0;
    for (const ep of endpoints) {
        const url = ep._id.url;
        const method = ep._id.method;

        db.single_type_info.updateOne(
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
        upserted++;
    }

    print(`  [${aktoId}] upserted ${upserted} single_type_info doc(s)`);
}

// ── Fix sample_data ──────────────────────────────────────────────────────────
print("\n── Updating sample_data ──");
for (const { aktoId, hostValue } of collectionsToFix) {
    const sampleDocs = db.sample_data.find({ "_id.apiCollectionId": aktoId }).toArray();

    for (const doc of sampleDocs) {
        const updatedSamples = (doc.samples || []).map(sampleStr => {
            try {
                const sample = JSON.parse(sampleStr);
                const requestHeaders = JSON.parse(sample.requestHeaders || "{}");
                if (!Object.keys(requestHeaders).map(k => k.toLowerCase()).includes("host")) {
                    requestHeaders["host"] = hostValue;
                    sample.requestHeaders = JSON.stringify(requestHeaders);
                }
                return JSON.stringify(sample);
            } catch (e) {
                return sampleStr;
            }
        });

        db.sample_data.updateOne(
            { _id: doc._id },
            { $set: { samples: updatedSamples } }
        );
    }

    print(`  [${aktoId}] updated ${sampleDocs.length} sample_data doc(s)`);
}

print("\nDone.");
