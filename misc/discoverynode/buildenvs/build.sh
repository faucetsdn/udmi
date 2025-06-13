#!/bin/bash -e
set -x

ENVS_DIR=$(realpath $(dirname $0))

if (( $# < 2 )); then
    echo "Usage: $0 IMAGE_REF BUILDENV [FORCE]"
    exit 1
fi

IMAGE_REF=$1
BUILDENV=$2
FORCE=$3

DOCKERFILE_PATH="$ENVS_DIR/$BUILDENV.Dockerfile"
if [[ ! -f $DOCKERFILE_PATH ]]; then
    echo "ERROR! could not find $DOCKERFILE_PATH"
    exit 1
fi

# Build if the image doesn't exist in the remot erepo
docker manifest inspect $IMAGE_REF > /dev/null || shouldbuild=y

# Else if there has been a change
git diff --quiet HEAD HEAD~1 -- "$DOCKERFILE_PATH" || shouldbuild=y

# Or we're being forced
[[ -n $FORCE ]] && shouldbuild=y

if [[ $shouldbuild != y ]]; then
    echo not building $BUILDENV because not found a reason to.
    echo exitting...
    exit
fi

docker build -f "$DOCKERFILE_PATH" -t $IMAGE_REF:latest .
docker push $IMAGE_REF:latest