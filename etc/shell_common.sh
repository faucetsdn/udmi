#
# General utility functions for working with UDMI scripts. Will be source'd by
# executing scripts.
#

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

function usage {
    echo usage: $0 $*
    false
}

function parse_project_id {
    project_target=x
    cloud_region=us-central1
    registry_prefix=z
    registry_id=q
    broker_hostname=yes
    protocol=mqtt
}

UDMI_JAR=$UDMI_ROOT/validator/build/libs/validator-1.0-SNAPSHOT-all.jar

udmi_version=$(cd $UDMI_ROOT; git describe --dirty --always)
export UDMI_TOOLS=$udmi_version
