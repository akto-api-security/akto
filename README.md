# Akto.io API Security

# What is Akto?

[How it works](https://docs.akto.io/#how-it-works) • [Getting-Started](https://docs.akto.io/#how-to-get-started) • [API Inventory](https://docs.akto.io/api-inventory/api-collections) • [API testing](https://docs.akto.io/testing/run-test) • [Add Test](https://docs.akto.io/testing/test-library) • [Join Discord community](https://discord.com/invite/Wpc6xVME4s) •

Akto is a plug-n-play API security platform that takes only 60 secs to get started. Akto is used by security teams to maintain a continuous inventory of APIs, test APIs for vulnerabilities and find runtime issues. Akto offers tests for all OWASP top 10 and HackerOne Top 10 categories including BOLA, authentication, SSRF, XSS, security configurations, etc. Akto's powerful testing engine runs variety of business logic tests by reading traffic data to understand API traffic pattern leading to reduced false positives. Akto can integrate with multiple traffic sources - burpsuite, AWS, postman, GCP, gateways, etc.

Akto enables security and engineering teams to secure their APIs by doing three things:

1. [API inventory](https://docs.akto.io/api-inventory/api-collections)
2. [Run business logic tests in CI/CD](https://docs.akto.io/testing/run-test)
3. [Find vulnerabilities in run-time](https://docs.akto.io/api-inventory/sensitive-data)


https://user-images.githubusercontent.com/91306853/216407351-d18c396b-5cd0-4cbc-a350-10a76b1d67b3.mp4

## How it works?

Step 1: Create inventory

<figure><img src="https://2145800921-files.gitbook.io/~/files/v0/b/gitbook-x-prod.appspot.com/o/spaces%2FRc4KTKGprZI2sPWKoaLe%2Fuploads%2FRXIYBFFP0cIi5gyJ02ZD%2FScreenshot%202023-01-26%20at%205.07.03%20PM.png?alt=media&token=d2976b86-d0cf-40f6-b17a-2611adceea05" alt=""><figcaption></figcaption></figure>

Step 2: Run tests

<figure><img src="https://2145800921-files.gitbook.io/~/files/v0/b/gitbook-x-prod.appspot.com/o/spaces%2FRc4KTKGprZI2sPWKoaLe%2Fuploads%2FPBJv5INL2k1UZOUXPbOG%2FScreenshot%202023-01-26%20at%205.08.19%20PM.png?alt=media&token=511b637c-1558-434a-b606-7983d24006a9" alt=""><figcaption></figcaption></figure>

## How to get Started?

Local deploy:

Run this script to create Akto at ~/akto and run the docker containers. You'll need to have Docker installed in order to run the container. 

`/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/akto-api-security/infra/feature/self_hosting/cf-deploy-akto)"`


## Develop and contribute

#### Prerequisites
OpenJDK 8, node(v18.7.0+ [link](https://nodejs.org/download/release/v18.7.0/)), npm(v8.15.0+), maven (v3.6.3 [link](https://dlcdn.apache.org/maven/maven-3/3.6.3/binaries/)), Mongo (v5.0.3+)


#### Clone repo
1. `mkdir ~/akto_code`
2. `cd akto_code`
3. `git clone https://github.com/akto-api-security/community-edition`

#### Setup database

1. `Open a new terminal tab`
2. `cd ~`
3. `mkdir ~/akto_mongo_data`
4. `<path_to_mongo_folder>/bin/mongod --dbpath ~/akto_mongo_data`

#### Setup Frontend

1. `Open a new terminal tab`
2. `cd ~/akto_code/community-edition`
3. `cd apps/dashboard`
4. `npm install`
5. `npm run hot`

#### Setup Dashboard

1. `Open a new terminal tab`
2. `cd ~/akto_code/community-edition`
3. `export AKTO_MONGO_CONN="mongodb://localhost:27017"`
4. `export DASHBOARD_MODE="local_deploy"`
5. `mvn clean install`
6. `mvn --projects :dashboard --also-make jetty:run`

##### Setup Testing

1. `Open a new terminal tab`
2. `cd ~/akto_code/community-edition`
3. `cd apps/testing`
4. `export AKTO_MONGO_CONN="mongodb://localhost:27017"`
5. `mvn compile; mvn exec:java -Dexec.mainClass="com.akto.testing.Main"`

#### Play around

1. Open `localhost:8080` in your favourite browser
2. You will need to signup when logging in for the first time, next time onwards you can login

#### Debug
1. To debug front end, install Vue.js Chrome extension from [here](https://devtools.vuejs.org/guide/installation.html).
2. To debug backend, run the following before running web server - 
    a. Set MAVEN_OPTS variable to enable debugging on your Java process
        
        ```bash
        export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8081, -Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.port=9010 -Dcom.sun.management.jmxremote.rmi.port=9010 -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
        ```
        
    b. In Visual Studio code, click on any line number to set a breakpoint.
    
    c.  Attach the Java debugger from Run and Debug mode. If you are doing this for the first time, click on “Create launch.json file” and then “Add configuration”. Choose “Java: Attach process by ID” and save the file. 
    
     <img width="426" alt="img1" src="https://user-images.githubusercontent.com/91221068/217048839-dbb00c48-00df-419b-8f32-cdb2d47a2218.png">

    d. A list of running Java processes with show up. Select the web server process to attach the debugger

## Contributing

We welcome contributions to this project. Please read our [CONTRIBUTING.md](CONTRIBUTING.md) for more information on how to get involved.

## License

This project is licensed under the [MIT License](LICENSE).
