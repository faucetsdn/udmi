#!/bin/bash -e

function usage(){
    echo Usage: $0 PATH_TO_SITE_MODEL PROJECT_ID
    exit 1
}

[[ $# -ne 2 ]] && usage

export SITE_PATH=$1
export SITE_PATH=$2

source venv/bin/activate

pytest test.py -n 5 -k test_device --durations=0
