<a href="https://artifacthub.io/packages/search?repo=akto" _target="blank">
  <img src="https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/akto"/>
</a>  


<a href="https://www.akto.io/blog/akto-takes-center-stage-at-black-hat-2023-in-las-vegas" _target="blank">
  <img src="https://img.shields.io/badge/Black_Hat_Arsenal-USA_2023-blue?style=square"/>
</a>  


<a href="https://www.akto.io/blog/akto-presentation-at-defcon-2023-in-las-vegas" _target="blank">
  <img src="https://img.shields.io/badge/Defcon-USA_2023-blue?style=square"/>
</a>  

<br/> 
<a href="https://github.com/akto-api-security/akto/commits/master" _target="blank">
  <img src="https://img.shields.io/github/commit-activity/m/akto-api-security/akto?label=commits&logo=github"/>
</a>  

<a href="https://github.com/akto-api-security/akto/releases" _target="blank">
  <img src="https://img.shields.io/github/release-date/akto-api-security/akto?label=latest%20release&logo=docker"/>
</a>

<a href="https://discord.gg/Wpc6xVME4s" _target="blank">
  <img src="https://img.shields.io/discord/1070706429402562733?logo=Discord"/>
</a>

<a href="https://hub.docker.com/r/aktosecurity/akto-api-security-dashboard/tags?page=1&name=local" _target="blank">
  <img src="https://img.shields.io/docker/image-size/aktosecurity/akto-api-security-dashboard?logo=docker"/>
</a>

<a href="https://github.com/akto-api-security/akto/issues?q=label%3Ahackfest" _target="blank">
  <img src="https://img.shields.io/github/issues/akto-api-security/akto/hackfest?logo=github"/>
</a>

<!--a href="https://hub.docker.com/r/aktosecurity/akto-api-security-dashboard" _target="blank">
  <img src="https://img.shields.io/docker/pulls/aktosecurity/akto-api-security-dashboard?logo=docker"/>
</a-->


<a href="https://hub.docker.com/r/aktosecurity/akto-api-security-dashboard" _target="blank">
  <img src="https://img.shields.io/badge/Docker_pulls-10K+-blue?logo=docker"/>
</a>


# Akto.io API Security

## Contributors
<a href="https://github.com/akto-api-security/akto/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=akto-api-security/akto" />
</a>


# What is Akto?

[How it works](https://docs.akto.io/#how-it-works) • [Getting-Started](https://docs.akto.io/#how-to-get-started) • [API Inventory](https://docs.akto.io/api-inventory/api-collections) • [API testing](https://docs.akto.io/testing/run-test) • [Add Test](https://docs.akto.io/testing/test-library) • [Join Discord community](https://discord.com/invite/Wpc6xVME4s) •

Akto is an instant, open source API security platform that takes only 60 secs to get started. Akto is used by security teams to maintain a continuous inventory of APIs, test APIs for vulnerabilities and find runtime issues. Akto offers coverage for all OWASP top 10 and HackerOne Top 10 categories including BOLA, authentication, SSRF, XSS, security configurations, etc. Akto's powerful testing engine runs variety of business logic tests by reading traffic data to understand API traffic pattern leading to reduced false positives. Akto can integrate with multiple traffic sources - burpsuite, AWS, postman, GCP, gateways, etc. Here is our [public roadmap](https://github.com/orgs/akto-api-security/projects/8) for this quarter.


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

### Using docker-compose (works for any machine which has Docker installed)
Run the following commands to install Akto. You'll need to have curl and Docker installed in order to run the container..
1. Clone the Akto repo by using this command `git clone https://github.com/akto-api-security/akto.git`
2. Go to the cloned directory `cd akto` 
3. Run `docker-compose up -d`
<details>
  <summary><h4>If you are setting this up in your own Cloud (AWS/GCP/Heroku), read this section</h4></summary>

Please ensure the following for good security practices
1. Open inbound security rule for port 9090 only. And restrict the source CIDR to VPC CIDR or your IP only. 
2. Use an EC2 from a private subnet - 
    
    a. This way, no one will be able to make an inbound request to your machine. 
    
    b. Ensure this private subnet has access to Internet so that outbound calls can succeed!
    
    c. You might have to set up tunneling to access instance via VPN using `ssh -i pemfile ec2-user@vpn-public-instance -L 9090:private-instance:9090`
    
    d. In your browser, visit `http://private-instance:9090`

3. Use an EC2 from a public subnet - please don't! If you still want to do this, you can skip 2.b and 2.c. Simply access your instance via `http://ip:9090`

Akto is really powerful in Cloud deployment if you can provide your application's mirrored traffic (0 performance impact). You would also be able to schedule tests in CI/CD and invite more team members on the dashboard. For that, you should install Akto Enterprise edition available [here](https://stairway.akto.io). Read more about it [here](https://www.akto.io/pricing)

</details>  
  
## API Security testing tutorials

| Title | Link |
| ------------- | ------------- |
| Introduction | https://www.youtube.com/watch?v=oFt4OVmfE2s |
| **Tutorial 1:** SSRF Port Scanning (OWASP API7:2023) | https://www.youtube.com/watch?v=WjNNh6asAD0 |


## Develop and contribute

<details>
  <summary><h3>Quicksetup using VSCode Devcontainers</h3></summary>

### Prerequisites:

1. [Install VSCode](https://code.visualstudio.com/)
2. [Install VSCode Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)  
3. **Windows:** [Docker Desktop](https://www.docker.com/products/docker-desktop) 2.0+ on Windows 10 Pro/Enterprise. Windows 10 Home (2004+) requires Docker Desktop 2.3+ and the [WSL 2 back-end](https://aka.ms/vscode-remote/containers/docker-wsl2). 
4. **macOS**: [Docker Desktop](https://www.docker.com/products/docker-desktop) 2.0+.
5. **Linux**: [Docker CE/EE](https://docs.docker.com/install/#supported-platforms) 18.06+ and [Docker Compose](https://docs.docker.com/compose/install) 1.21+.

**Note**: If using Docker Desktop, consider changing the memory allocation to 8 GB for better performance  
  
### Steps:

#### Clone repo and open in vscode

1. Open terminal
2. `mkdir ~/akto_code`
3. `cd ~/akto_code`
4. `git clone https://github.com/akto-api-security/akto`
5. Open in VScode: `code akto`

#### Start Dev Container

1. Go to View > Command Palette and type: Dev Containers: Reopen in Container
<img src="https://user-images.githubusercontent.com/125550503/225829693-0c627020-9fe3-4738-80e0-39f076780c3b.png"></img>
2. Wait for the Dev Container to set up.
3. Open **localhost:9090** in your web browser to see the Akto dashboard

</details>


<details>
  <summary><h3> Manual Setup Instructions</h3> </summary>

### Prerequisites
OpenJDK 8, node(v18.7.0+ [link](https://nodejs.org/download/release/v18.7.0/)), npm(v8.15.0+), maven (v3.6.3 [link](https://dlcdn.apache.org/maven/maven-3/3.6.3/binaries/)), MongoDB (v5.0.3+ [link](https://www.mongodb.com/docs/manual/administration/install-community/))


#### Clone repo
1. `mkdir ~/akto_code`
2. `cd akto_code`
3. `git clone https://github.com/akto-api-security/akto`

#### Setup database

1. `Open a new terminal tab`
2. `cd ~`
3. `mkdir ~/akto_mongo_data`
4. `<path_to_mongo_folder>/bin/mongod --dbpath ~/akto_mongo_data`

#### Setup Frontend

1. `Open a new terminal tab`
2. `cd ~/akto_code/akto`
3. `cd apps/dashboard/web/polaris_web`
4. `npm install`
5. `npm run hot`

#### Setup Dashboard

1. `Open a new terminal tab`
2. `cd ~/akto_code/akto`
3. `export AKTO_MONGO_CONN="mongodb://localhost:27017"`
4. `export DASHBOARD_MODE="local_deploy"`
5. `mvn clean install`
6. `mvn --projects :dashboard --also-make jetty:run -Djetty.port=9090`

#### Setup Testing

1. `Open a new terminal tab`
2. `cd ~/akto_code/akto`
3. `cd apps/testing`
4. `export AKTO_MONGO_CONN="mongodb://localhost:27017"`
5. `mvn compile; mvn exec:java -Dexec.mainClass="com.akto.testing.Main"`

  </details>  
  
#### Using Testing CLI tool

Run the following command to run testing CLI tool

```bash
docker run -v ./:/out  \ # needed to generate test report on host machine
    -e TEST_IDS='JWT_NONE_ALGO REMOVE_TOKENS' \ # space separated test ids
    -e AKTO_DASHBOARD_URL='<AKTO_DASHBOARD_URL>' \ 
    -e AKTO_API_KEY='<AKTO_API_KEY>' \ 
    -e API_COLLECTION_ID='123' \ # api collection id on which you want to run tests
    -e TEST_APIS='https://demo.com/api/books https://demo.com/api/cars' \ # space separated apis from the api collection on which you want to run tests. If not present, all apis in the collection will be tested. [optional]
    -e OVERRIDE_APP_URL='https://dummy.com' \ # If you want to test on a separate host. [optional] 
    aktosecurity/akto-api-testing-cli
```

### Play around

1. Open `localhost:9090` in your favourite browser
2. You will need to signup when logging in for the first time, next time onwards you can login

<details>  
  <summary><h3>Debug</h3></summary>
1. To debug front end, install Vue.js Chrome extension from [here](https://devtools.vuejs.org/guide/installation.html).
2. To debug backend, run the following before running web server - 
  a. Set MAVEN_OPTS variable to enable debugging on your Java process
        
        export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8081, -Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.port=9010 -Dcom.sun.management.jmxremote.rmi.port=9010 -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
        
  b. In Visual Studio code, click on any line number to set a breakpoint.
    
  c.  Attach the Java debugger from Run and Debug mode. If you are doing this for the first time, click on “Create launch.json file” and then “Add configuration”. Choose “Java: Attach process by ID” and save the file. <br/>
     <img width="426" alt="img1" src="https://user-images.githubusercontent.com/91221068/217048839-dbb00c48-00df-419b-8f32-cdb2d47a2218.png"><br/>
  d. A list of running Java processes with show up. Select the web server process to attach the debugger

</details>  
<a href="https://hits.sh/github.com/akto-api-security/hits.svg?label=Hits%20since%2020%2F5&color=FFFFFF&labelColor=FFFFFF"><img alt="Hits" src="https://hits.sh/github.com/akto-api-security/hits.svg?label=Hits%20since%2020%2F5&color=FFFFFF&labelColor=FFFFFF"/></a> 
  
## Contributing

We welcome contributions to this project. Please read our [CONTRIBUTING.md](CONTRIBUTING.md) for more information on how to get involved.

## License

This project is licensed under the [MIT License](LICENSE.md).
