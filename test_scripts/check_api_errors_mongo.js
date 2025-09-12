// MongoDB query script to check for apiErrors field
// Usage: mongosh --file check_api_errors_mongo.js

const DB_ID = process.env.AKTO_DB_ID || '1000000';
use(DB_ID);

print(`Collections in database ${DB_ID}:`);
db.getCollectionNames().forEach(function(c){ print('- ' + c); });

print("\n=== Checking testing_run_result collection ===");
const totalCount = db.testing_run_result.countDocuments();
print("Total documents in testing_run_result: " + totalCount);

const withApiErrors = db.testing_run_result.countDocuments({ apiErrors: { $exists: true } });
print("Documents with apiErrors field: " + withApiErrors);

print("\n=== Latest docs for /test/api GET ===");
db.testing_run_result.find({ 'apiInfoKey.url': '/test/api', 'apiInfoKey.method': 'GET' }, {
  _id: 1,
  apiInfoKey: 1,
  apiErrors: 1,
  testResults: { $slice: 1 }
}).sort({ _id: -1 }).limit(5).forEach(function(doc){
  print("ID: " + doc._id);
  print("API Key: " + JSON.stringify(doc.apiInfoKey));
  print("API Errors: " + JSON.stringify(doc.apiErrors));
  if (doc.testResults && doc.testResults[0]) {
    print("Message: " + doc.testResults[0].message);
  }
  print("---");
});



