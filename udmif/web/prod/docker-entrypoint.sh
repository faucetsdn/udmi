#!/usr/bin/env sh
set -eu

# Replace variables in the env 'template' file with Docker
# runtime environment variables. Output a renamed env.js file.
envsubst '${GOOGLE_CLIENT_ID} ${API_URI}' < env.template.js > env.js

exec "$@"
