# set default environment variables pointing to a local postgresql instance
export POSTGRESQL_INSTANCE_HOST=localhost
export POSTGRESQL_PORT=5432
export POSTGRESQL_USER=postgres
export POSTGRESQL_PASSWORD=
export POSTGRESQL_DATABASE=udmi

# run the application while watching for file changes
npm run migrate