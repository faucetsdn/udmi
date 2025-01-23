# Utility to stream logs from any process to gSheets
# Will be sourced by other executing scripts
#

UDMI_ROOT=$(realpath $(dirname $0)/..)

source $UDMI_ROOT/etc/shell_common.sh

[[ ! -d $UDMI_ROOT/validator/src ]] ||
    up_to_date $UDMI_JAR $UDMI_ROOT/validator/src ||
    $UDMI_ROOT/validator/bin/build

STREAM_CLASS=com.google.udmi.util.SheetsOutputStream

stream_to_gsheets() {
  local tool_name=$1
  local sheet_id=$2
  timestamp=$(date +%Y%m%d_%H%M%S)
  java -cp "$UDMI_JAR" $STREAM_CLASS "$tool_name" "$sheet_id" \
  "$tool_name.$timestamp.log"
}
