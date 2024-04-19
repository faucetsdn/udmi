#!/bin/bash

echo $1

bin/keygen RS256 sites/bos-platform-prod/$1/udmi/reflector
cd sites/bos-platform-prod/$1/
git status
git add .
git commit -m "generated UDMI site model"
git push -o nokeycheck


