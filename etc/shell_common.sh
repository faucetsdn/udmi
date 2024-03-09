#
# General utility functions for working with UDMI scripts. Will be source'd by
# executing scripts.
#

# Force consistent sort order
export LC_ALL=C

#
# Walk up the directory tree looking for the cloud_iot_config.json site model marker.
# If not found, just return empty.
#
function find_site_model_root {
    while [ $PWD != "/" ]; do
        test -f cloud_iot_config.json && { pwd; break; }
        cd ..
    done
}

function search {
    egrep "$1" $2 || fail Could not find in $2: $1
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
    
UDMI_JAR=$UDMI_ROOT/validator/build/libs/validator-1.0-SNAPSHOT-all.jar

udmi_version=$(cd $UDMI_ROOT; git describe --dirty --always)
export UDMI_TOOLS=$udmi_version
