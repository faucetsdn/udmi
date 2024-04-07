#
# General utility functions for working with UDMI scripts. Will be source'd by
# executing scripts.
#

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
    
UDMI_JAR=$UDMI_ROOT/validator/build/libs/validator-1.0-SNAPSHOT-all.jar

udmi_version=$(cd $UDMI_ROOT; git describe --dirty --always)
export UDMI_TOOLS=$udmi_version
