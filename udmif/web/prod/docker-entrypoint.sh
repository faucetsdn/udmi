#!/usr/bin/env sh
set -eu

# Replace variables in the env 'template' file with Docker runtime environment variables.
# Output a regular env.conf file in the nginx's web directory with the variables replaced.
envsubst '${GOOGLE_CLIENT_ID}' < src/env.template.js > \$WEB_DIR/env.js

exec "$@"
