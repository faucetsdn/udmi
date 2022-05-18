# Locally transpille the typescript code so terraform can deploy it properly.

export MONGO_PROTOCOL=mongodb
export MONGO_HOST=localhost
export MONGO_DATABASE=device 

rm -rf dist
rm index.zip

mkdir dist
cp package.json dist
npm install
npm run build