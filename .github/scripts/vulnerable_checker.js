const axios = require("axios")
const fs = require('fs');

const AKTO_DASHBOARD_URL = process.env.AKTO_DASHBOARD_URL
const AKTO_API_KEY = process.env.AKTO_API_KEY
const GITHUB_STEP_SUMMARY = process.env.GITHUB_STEP_SUMMARY

const ignore = {
  missing: [
    'ADD_USER_ID',
    'GRAPHQL_DEBUG_MODE_ENABLED',
    'GRAPHQL_FIELD_SUGGESTIONS_ENABLED',
    'MASS_ASSIGNMENT_CHANGE_ACCOUNT',
    'ADD_JKU_TO_JWT',
    'JWT_INVALID_SIGNATURE',
    'MASS_ASSIGNMENT_CHANGE_ADMIN_ROLE',
    'HEAD_METHOD_TEST',
    'RANDOM_METHOD_TEST',
    'JWT_SIGNING_IN_CLIENT_SIDE',
    'GRAPHQL_TYPE_INTROSPECTION_ALLOWED',
    'GRAPHQL_DEVELOPMENT_CONSOLE_EXPOSED',
    'PARAMETER_POLLUTION',
    'GRAPHQL_INTROSPECTION_MODE_ENABLED'
  ],
  notVulnerable: [
    'TEXT_INJECTION_VIA_INVALID_URLS',
    'CORS_MISCONFIGURATION_INVALID_ORIGIN',
    'SSRF_ON_LOCALHOST',
    'MASS_ASSIGNMENT_CHANGE_ROLE',
    'XSS_IN_PATH',
    'DOCKERFILE_HIDDEN_DISCLOSURE',
    'TRACE_METHOD_TEST',
    'CORS_MISCONFIGURATION_WHITELIST_ORIGIN',
    'LARAVEL_DEBUG_MODE_ENABLED',
    'CONFIG_JSON',
    'RAILS_DEBUG_MODE_ENABLED',
    'CONTENT_TYPE_HEADER_MISSING'
  ]
}

const headers = {
  'X-API-KEY': AKTO_API_KEY,
}


function mapTestsToVulnerableRequests(vulnerableRequests) {
  const mapRequestsToId = {}

  vulnerableRequests.forEach((x)=>{
      let methodArr = x.templateIds
      methodArr.forEach((method) => {
          if (!mapRequestsToId[method]) {
              mapRequestsToId[method] = {}
          }

          mapRequestsToId[method] = x.id
      })
  })

  return mapRequestsToId
}

function logGithubStepSummary(message) {
  fs.appendFileSync(GITHUB_STEP_SUMMARY, `${message}\n`);
}

async function main() {

  logGithubStepSummary("### Vulnerable API's summary")

  try {
    const allSubCategoriesResponse = await axios.post(`${AKTO_DASHBOARD_URL}/api/fetchAllSubCategories`, {}, { headers })
    
    if (allSubCategoriesResponse.status === 200) {
      const missing = []
      const vulnerableRequests = allSubCategoriesResponse.data.vulnerableRequests
      const mapRequestsToId = mapTestsToVulnerableRequests(vulnerableRequests)
      
      const testDetails = allSubCategoriesResponse.data.subCategories

      logGithubStepSummary("#### Not vulnerable  ")
      logGithubStepSummary("S.No | Template | Endpoint ")
      logGithubStepSummary("--- | --- | ---")
      let counter = 1

      for(const testDetail of testDetails) {
        if (testDetail.templateSource._name === "AKTO_TEMPLATES") {
          console.log(`Verifying ${testDetail.testName}`)
          if (!mapRequestsToId[testDetail.name]) {
            missing.push({ name: testDetail.name, testName: testDetail.testName })
            continue
          }

          const { apiCollectionId, url, method: { _name: method }} =  mapRequestsToId[testDetail.name]
          const sampleDataResponse = await axios.post(`${AKTO_DASHBOARD_URL}/api/fetchSampleData`, 
            { apiCollectionId, url, method },
            { headers }
          )
          const sampleDataList = sampleDataResponse.data.sampleDataList


          const runTestForGivenTemplateResponse = await axios.post(`${AKTO_DASHBOARD_URL}/api/runTestForGivenTemplate`, 
            { apiInfoKey: { apiCollectionId, method, url }, 
              content: testDetail.content, 
              sampleDataList
            },
            { headers }
          )

          if (runTestForGivenTemplateResponse.data.testingRunResult.vulnerable === false && !ignore.notVulnerable.includes(testDetail.name)) {
            logGithubStepSummary(`${counter} | ${testDetail.testName}(${testDetail.name}) | ${url}`)
            counter += 1
          }
        }
      }

      logGithubStepSummary("#### Missing ")
      logGithubStepSummary("S.No | Template ")
      logGithubStepSummary("--- | --- ")
      counter = 1
      for(const template of missing) {
        if (!ignore.missing.includes(template.name)) {
          logGithubStepSummary(`${counter} | ${template.testName}(${template.name})`)
        }
        counter += 1
      }
    }
  } catch (error) {
    console.error('Error:', error.message);
  }
}

// Schedule the script to run at specific intervals (e.g., every 24 hours)

const scheduleIntervalHours = 24; // Adjust interval as needed
setInterval(main, scheduleIntervalHours * 60 * 60 * 1000);

main()
