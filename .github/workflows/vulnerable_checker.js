const axios = require("axios")
const fs = require('fs');

const AKTO_DASHBOARD_URL = process.env.AKTO_DASHBOARD_URL
const AKTO_API_KEY = process.env.AKTO_API_KEY
const GITHUB_STEP_SUMMARY = process.env.GITHUB_STEP_SUMMARY

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

          if (runTestForGivenTemplateResponse.data.testingRunResult.vulnerable === false) {
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
        logGithubStepSummary(`${counter} | ${template.testName}(${template.name})`)
        counter += 1
      }
    }
  } catch (error) {
    console.error('Error:', error.message);
  }
}

main()