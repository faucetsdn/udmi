echo "Building development test container for UDMIF API..."
docker build -f dev/Dockerfile -t udmif-api-test .

echo "Running npm install and test..."
docker run -it --rm \
                    --mount "source=${PWD},target=/home/api,type=bind,consistency=delegated" \
                    --name udmif-api-test udmif-api-test \
                    npm install && npm test
                     
