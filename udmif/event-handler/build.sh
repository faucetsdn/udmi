# Locally transpille the typescript code so terraform can deploy it properly.

rm -rf dist
rm index.zip
npm run build