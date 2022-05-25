# Locally transpille the typescript code so terraform can deploy it properly.

export MONGO_PROTOCOL=mongodb
export MONGO_HOST=localhost
export MONGO_DATABASE=udmi 

npm run watch