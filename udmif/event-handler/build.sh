 #!/bin/bash line
# Locally transpille the typescript code so it can be deployed to GCP properly.
rm -rf dist
mkdir dist

cp prod-package.json dist/package.json
npm install
npm run build
rm -rf dist/*test*