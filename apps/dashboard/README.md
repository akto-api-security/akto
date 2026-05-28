To setup node modules 
`npm install`

To build Vue
`npm run build`

Build dao by running this command from dao project
`~/Downloads/apache-maven-3.6.3/bin/mvn install`

Run using
` ~/Downloads/apache-maven-3.6.3/bin/mvn  jetty:run`

Open 
`http://localhost:8080/`

### Dashboard static assets

Maps and login vendor scripts under `web/public/` are refreshed in CI via `apps/dashboard/scripts/download-dashboard-vendor-assets.sh`. Other images (empty state, YouTube thumbnails) are committed static files only.

How to setup git config locally
`https://gist.github.com/Jonalogy/54091c98946cfe4f8cdab2bea79430f9`


