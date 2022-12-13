# set default environment variables pointing to a local mongodb instance
export MONGO_PROTOCOL=mongodb
export MONGO_HOST=localhost
export MONGO_DATABASE=udmi

# set default environment variables pointing to a local postgresql instance
export POSTGRESQL_INSTANCE_HOST=127.0.0.1
export POSTGRESQL_PORT=5432
export POSTGRESQL_USER=postgres
export POSTGRESQL_PASS=
export POSTGRESQL_DATABASE=udmif

# run the application while watching for file changes
npm start