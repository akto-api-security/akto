import { createRunner, PuppeteerRunnerExtension } from '@puppeteer/replay';
import puppeteer from 'puppeteer';

import fs from 'fs'

var body = process.argv[4]

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
  }

  async afterEachStep(step, flow) {
    await super.afterEachStep(step, flow);

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

      })
      
      
    })
  }

  async afterAllSteps(flow) {

    var currentDir = process.cwd()
    await super.afterAllSteps(flow);
    
    await page.waitForNavigation({waitUntil: 'networkidle0'})
    
    const href = await page.evaluate(() =>  window.location.href);

    page.evaluate((x) => cookieMap = x, tokenMap);

    let command = process.argv[2];
    const localStorageValues = await page.evaluate((x) => eval(x), command);

    var token = String(localStorageValues)
    var createdAt = Math.floor(Date.now()/1000)
    var output = `{"token": "${token}", "created_at": ${createdAt}}`

    //var outputFilePath = currentDir + '/output.json'
    var outputFilePath = process.argv[3]
    fs.writeFileSync(outputFilePath, output);

    await browser.close();
  }
}

export const flow = bodyObj;

const runner = await createRunner(
  flow,
  new Extension(browser, page, 7000)
);

await runner.run();
