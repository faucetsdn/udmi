
UDMI_ROOT=$(realpath $(dirname $(readlink -f $0))/..)

source $UDMI_ROOT/etc/shell_common.sh

source $UDMI_ROOT/etc/find_udmi_profile.sh

source $UDMI_ROOT/etc/extract_parameters.sh
