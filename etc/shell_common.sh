#
# General utility functions for working with UDMI scripts. Will be source'd by
# executing scripts.
#

# Be agressive with error handling
set -eu
set -o pipefail

# Force consistent sort order
export LC_ALL=C

function search {
    egrep -q "$1" $2 || fail Could not find in $2: $1
}

function fail {
    echo error: $*
    false
}

function warn {
    echo warning: $*
}

function usage {
    echo usage: $0 $*
    false
}

PUBBER_LOG=out/pubber.log
PUBBER_WAIT=30
function pubber_bg {
    device_id=$1
    shift
    outfile=$PUBBER_LOG.$device_id
    serial_no=pubber-$RANDOM

    device_dir=$site_path/devices/$device_id

    echo bin/keygen CERT $device_dir
    bin/keygen CERT $device_dir || true

    echo Writing pubber output to $outfile, serial no $serial_no
    cmd="bin/pubber $site_path $project_spec $device_id $serial_no $@"
    echo $cmd

    date > $outfile
    echo $cmd >> $outfile
    $cmd >> $outfile 2>&1 &

    # Give a little bit of time to settle before deterministic check

    for count in `seq 0 $PUBBER_WAIT`; do
        if fgrep "Connection complete" $outfile; then
            break
        fi
        echo Waiting for pubber startup $((PUBBER_WAIT - count))...
        sleep 1
    done

    if [[ $count -eq $PUBBER_WAIT ]]; then
        echo pubber startup failed:
        cat $outfile
        return 1
    fi
}

UDMI_ROOT=$(realpath $UDMI_ROOT)

UDMI_JAR=$UDMI_ROOT/validator/build/libs/validator-1.0-SNAPSHOT-all.jar

udmi_version=$(cd $UDMI_ROOT; git describe --dirty) || true

[[ -z $udmi_version ]] && udmi_version=git-$(cd $UDMI_ROOT; git describe --dirty --always) || true

[[ $udmi_version == git- ]] && udmi_version=unknown

export UDMI_ROOT
export UDMI_TOOLS=$udmi_version
