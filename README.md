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
OpenJDK 8, node, npm, maven (v3.6.3), Mongo


#### Clone repo
1. `mkdir ~/akto_code`
2. `cd akto_code`
3. `git clone https://github.com/akto-api-security/community-edition`

#### Setup database

1. `cd ~`
2. `mkdir ~/akto_mongo_data`
3. `<path_to_mongo_folder>/bin/mongod --dbpath ~/akto_mongo_data`

#### Setup Frontend

1. `cd ~/akto_code/community-edition`
2. `cd apps/dashboard`
3. `npm install`
4. `npm run hot`

#### Setup Backend
1. `cd ~/akto_code/community-edition`
2. `export AKTO_MONGO_CONN="mongodb://localhost:27017"`
3. `mvn clean install`
4. `mvn --projects :dashboard --also-make jetty:run`

#### Play around
1. Open `localhost:8080` in your favourite browser
2. You will need to signup when logging in for the first time, next time onwards you can login

## Contributing

We welcome contributions to this project. Please read our [CONTRIBUTING.md](CONTRIBUTING.md) for more information on how to get involved.

## License

This project is licensed under the [MIT License](LICENSE).
