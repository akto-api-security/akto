import { createRunner, PuppeteerRunnerExtension } from '@puppeteer/replay';
import puppeteer from 'puppeteer';

import fs from 'fs'

var body = process.argv[3]

var bodyObj = JSON.parse(body);

const browser = await puppeteer.launch({
  headless: false,
});

const tokenMap = {};

const page = await browser.newPage();

page.setDefaultNavigationTimeout(20000);

class Extension extends PuppeteerRunnerExtension {
  async beforeEachStep(step, flow) {
    await super.beforeEachStep(step, flow);
    //console.log("before", step)
    //fs.appendFileSync('/Users/admin/akto_code/mono/apps/dashboard/src/main/java/com/akto/action/testing/recordedLoginFlowScript/beforestep.json', ","+JSON.stringify(step));
  }

  async afterEachStep(step, flow) {
    await super.afterEachStep(step, flow);
   // console.log("after", step)
    //fs.appendFileSync('/Users/admin/akto_code/mono/apps/dashboard/src/main/java/com/akto/action/testing/recordedLoginFlowScript/afterstep.json', ","+JSON.stringify(step));

    let pages = await browser.pages()
    pages.forEach(_page => {
      _page.on("response", async resp => {
        var headers = resp.headers()
        for (let key in headers) {
          if (key === 'set-cookie') {
            var tokenObj = headers[key].split(';')[0]
            var tokenKey = tokenObj.split('=')[0]
            var tokenVal = tokenObj.split('=')[1]
            tokenMap[tokenKey] = tokenVal
          }
        }
      
        //const response_json = {};//await resp.json()
       // console.log(resp.url(), headers, response_json)
      })
      
      
    })
  }

  async afterAllSteps(flow) {

    await super.afterAllSteps(flow);
    
    await page.waitForNavigation({waitUntil: 'networkidle0'})
    
    const href = await page.evaluate(() =>  window.location.href);

    // console.log("tokenMap value")
    // console.log(tokenMap)

    page.evaluate((x) => cookieMap = x, tokenMap);

    let command = process.argv[2];
    const localStorageValues = await page.evaluate((x) => eval(x), command);
    console.log("localStorageValues")
    console.log(localStorageValues)

    var token = String(localStorageValues)
    var createdAt = Math.floor(Date.now()/1000)
    var output = `{"token": "${token}", "created_at": ${createdAt}}`

    // todo: recheck file url
    fs.writeFileSync('/Users/admin/akto_code/mono/apps/dashboard/src/main/java/com/akto/action/testing/recordedLoginFlowScript/output.json', output);

    await browser.close();
  }
}

export const flow = bodyObj;

const runner = await createRunner(
  flow,
  new Extension(browser, page, 7000)
);

await runner.run();