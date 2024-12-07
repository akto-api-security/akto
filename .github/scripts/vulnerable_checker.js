const axios = require("axios")
const fs = require('fs');

const AKTO_DASHBOARD_URL = process.env.AKTO_DASHBOARD_URL
const AKTO_API_KEY = process.env.AKTO_API_KEY
const GITHUB_STEP_SUMMARY = process.env.GITHUB_STEP_SUMMARY

const ignore = {
  missing: [
    'BFLA_INSERT_ADMIN_IN_URL_PATHS',
    'SSRF_ON_XML_UPLOAD_LOCALHOST_REDIRECT',
    'SSL_ENABLE_CHECK',
    'BFLA_WITH_PUT_METHOD',
    'BFLA_REPLACE_ADMIN_IN_URL_PATHS',
    'BFLA_WITH_POST_METHOD',
    'USER_ENUM_REDIRECT_PAGE',
    'AUTH_BYPASS_STAGING_URL',
    'SSRF_ON_IMAGE_UPLOAD_GCP_REDIRECT',
    'SSRF_ON_PDF_UPLOAD_AWS_REDIRECT',
    'BFLA_WITH_PATCH_METHOD',
    'SSRF_ON_XML_UPLOAD_AZURE_REDIRECT',
    'SSRF_ON_CSV_UPLOAD_AWS_REDIRECT',
    'RANDOM_METHOD_TEST',
    'SSRF_ON_LOCALHOST_DNS_PINNING',
    'BFLA_WITH_GET_METHOD',
    'SSRF_ON_IMAGE_UPLOAD_LOCALHOST_REDIRECT',
    '2FA_BROKEN_LOGIC_AUTH_TOKEN_TEST',
    'SSRF_ON_CSV_UPLOAD_GCP_REDIRECT',
    'SSRF_ON_CSV_UPLOAD_LOCALHOST_REDIRECT',
    'SSRF_ON_PDF_UPLOAD_GCP_REDIRECT',
    'SSRF_ON_PDF_UPLOAD_LOCALHOST_REDIRECT',
    'LOGOUT_AUTH_TOKEN_TEST',
    'SSRF_SCRIPT_TAG_AZURE_REDIRECT',
    'SSL_ENABLE_CHECK_AUTH',
    'SSRF_SCRIPT_TAG_AWS_REDIRECT',
    'SSRF_ON_LOCALHOST',
    'SSRF_SCRIPT_TAG_BASIC',
    'DOCKERFILE_HIDDEN_DISCLOSURE',
    'SSRF_ON_XML_UPLOAD_GCP_REDIRECT',
    'SSRF_ON_IMAGE_UPLOAD_AZURE_REDIRECT',
    'SSRF_ON_LOCALHOST_ENCODED',
    'AUTH_BYPASS_LOCKED_ACCOUNT_TOKEN_ROLE',
    'BOLA_ADD_CUSTOM_HEADER',
    'SSRF_ON_PDF_UPLOAD_AZURE_REDIRECT',
    'SSRF_SCRIPT_TAG_LOCALHOST_REDIRECT',
    'USER_ENUM_ACCOUNT_LOCK',
    'SSRF_ON_IMAGE_UPLOAD_AWS_REDIRECT',
    'SSRF_ON_CSV_UPLOAD_AZURE_REDIRECT',
    'SSRF_SCRIPT_TAG_GCP_REDIRECT',
    'BASIC_BFLA',
    'SSRF_ON_XML_UPLOAD_AWS_REDIRECT',
  ],
  notVulnerable: [
    'DOS_TEST_URL',
    'TRACE_METHOD_TEST',
    'USER_ENUM_FOLDER_ACCESS',
    'REMOVE_TOKENS',
    'TEXT_INJECTION_VIA_INVALID_URLS',
    'XSS_IN_PATH',
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

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
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

        await sleep(500)
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
