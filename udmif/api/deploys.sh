#!/bin/bash

helpFunction()
{
   echo ""
   echo "Usage: $0 -p <project_id> -t <tag>"
   echo -e "\t-p Enter gcp project_id"
   echo -e "\t-t Docker image tag"
   exit 1 # Exit script after printing help
}

while getopts "p:t:" opt
do
   case "$opt" in
      p ) projectId="$OPTARG" ;;
      t ) tag="$OPTARG" ;;
      ? ) helpFunction ;; # Print helpFunction in case parameter is non-existent
   esac
done

# Print helpFunction in case parameters are empty
if [ -z "$projectId" ] || [ -z "$tag" ]
then
   echo "Missing some of the parameters";
   helpFunction
fi


docker build -t udmif-api:$tag .
docker tag udmif-api:$tag gcr.io/$projectId/udmif-api:$tag
docker push gcr.io/$projectId/udmif-api:$tag
