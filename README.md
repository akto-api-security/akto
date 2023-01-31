# Akto.io API Security

This is an open-source API security product that helps to protect your API from various attacks such as SQL injection, cross-site scripting (XSS), and cross-site request forgery (CSRF).

## Features

- Get your API Inventory
- Run automated tests for BOLA, BUA, JWT tests
- Import data from Burpsuite and Postman

## Getting Started

### Quick local deploy
Execute the following command: 
`/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/akto-api-security/infra/feature/self_hosting/cf-deploy-akto)"`

### Deploy (Suggest better title here)

#### Prerequisites
OpenJDK 8, node, npm, maven (v3.6.3), Mongo


#### Clone repo
1. `mkdir ~/akto_code`
2. `cd akto_code`
3. `git clone https://github.com/akto-api-security/mono` Link to be updated as per the open source repo

#### Setup database

1. `cd ~`
2. `mkdir ~/akto_mongo_data`
3. `<path_to_mongo_folder>/bin/mongod --dbpath ~/akto_mongo_data`

#### Setup Frontend

1. `cd ~/akto_code/mono`
2. `cd apps/dashboard`
3. `npm install`
4. `npm run hot`

#### Setup Backend
1. `cd ~/akto_code/mono`
2. `export AKTO_MONGO_CONN="mongodb://localhost:27017"`
3. `mvn clean install`
4. `mvn --projects :dashboard --also-make jetty:run`

#### Play around
1. Open `localhost:8080` in your favourite browser
2. You will need to signup when logging in for the first time, next time onwards you can login

## Usage

You can use this product as a middleware in your express.js or any other node.js based web application.

## Contributing

We welcome contributions to this project. Please read our [CONTRIBUTING.md](CONTRIBUTING.md) for more information on how to get involved.

## License

This project is licensed under the [MIT License](LICENSE).
