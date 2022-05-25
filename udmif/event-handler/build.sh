 #!/bin/bash line
# Locally transpille the typescript code so it can be deployed to GCP properly.

export MONGO_PROTOCOL=mongodb
export MONGO_HOST=localhost
export MONGO_DATABASE=device 

rm -rf dist
mkdir dist

cp prod-package.json dist/package.json
npm install
npm run build
rm -rf dist/*test*