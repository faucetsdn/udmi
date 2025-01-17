#!/bin/bash

UDMI_ROOT=$(realpath $(dirname $0)/..)

source $UDMI_ROOT/etc/shell_common.sh

[[ ! -d $UDMI_ROOT/validator/src ]] ||
    up_to_date $UDMI_JAR $UDMI_ROOT/validator/src ||
    $UDMI_ROOT/validator/bin/build

STREAM_CLASS=com.google.udmi.util.SheetsOutputStream
timestamp=$(date +%Y%m%d_%H%M%S)

stream_to_gsheets() {
  local util_name=$1
  local sheet_id=$2
  java -cp "$UDMI_JAR" $STREAM_CLASS "$util_name" "$sheet_id" \
  "$util_name.$timestamp.log"
}
