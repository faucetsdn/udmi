#!/bin/bash

UDMI_ROOT=$(realpath $(dirname $0)/..)
UDMIS_JAR=$UDMI_ROOT/udmis/build/libs/udmis-1.0-SNAPSHOT-all.jar



source $UDMI_ROOT/etc/shell_common.sh

if [[ ! -f "$UDMIS_JAR" ]]; then
  $UDMI_ROOT/udmis/bin/build
fi

JAVA_CLASS=com.google.udmi.util.GSheetsOutputStream

timestamp=$(date +%Y%m%d_%H%M%S)
stream_to_gsheets() {
  java -cp $UDMIS_JAR $JAVA_CLASS "registrar" \
  "12leoCVQpdXx6MdKlVCCI4O1mi35dOTw_FxDMBetuwXQ" "registrar.$timestamp.log"
}

