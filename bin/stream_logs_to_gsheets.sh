#!/bin/bash

UDMI_ROOT=$(realpath $(dirname $0)/..)

source $UDMI_ROOT/etc/shell_common.sh

[[ ! -d $UDMI_ROOT/validator/src ]] ||
    up_to_date $UDMI_JAR $UDMI_ROOT/validator/src ||
    $UDMI_ROOT/validator/bin/build

JAVA_CLASS=com.google.udmi.util.GSheetsOutputStream

timestamp=$(date +%Y%m%d_%H%M%S)
stream_to_gsheets() {
  java -cp $UDMI_JAR $JAVA_CLASS "registrar" \
  "12leoCVQpdXx6MdKlVCCI4O1mi35dOTw_FxDMBetuwXQ" "registrar.$timestamp.log"
}

