#
# General utility functions for working with UDMI scripts. Will be source'd by
# executing scripts.
#

# Be agressive with error handling
set -eu
set -o pipefail

# Force consistent sort order and other processing things
export LC_ALL=en_US.UTF-8

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

function newest_file {
    echo $(find ${1:-.} -type f -printf '%T@ %p\n' | sort -n | tail -1 | cut -d' ' -f 2-)
}

function up_to_date {
    target=$1
    [[ ! -f $target ]] && return 1
    newest=$(newest_file $2)
    [[ $target -ot $newest ]] && return 1
    common=$(newest_file $UDMI_ROOT/common/src)
    [[ $target -ot $common ]] && return 1
    return 0
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

VERSION_BASE='1.*'

# Ignore non-version branches (e.g. something like 'develop')
udmi_version=$(cd $UDMI_ROOT; git describe --dirty --match $VERSION_BASE) || true

# No luck... just generate any viable version.
[[ -n $udmi_version ]] || udmi_version=git-$(cd $UDMI_ROOT; git describe --dirty --match $VERSION_BASE --always) || true

[[ $udmi_version == git- ]] && udmi_version=unknown

export UDMI_ROOT
export UDMI_TOOLS=$udmi_version
