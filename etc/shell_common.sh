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

[[ -n ${UDMI_ROOT:-} ]] || UDMI_ROOT=$PWD
UDMI_ROOT=$(realpath $UDMI_ROOT)

UDMI_JAR=$UDMI_ROOT/validator/build/libs/validator-1.0-SNAPSHOT-all.jar
SERVICES_JAR=$UDMI_ROOT/services/build/libs/services-1.0-SNAPSHOT-all.jar
VERSION_BASE='1.*'

# Ignore non-version branches (e.g. something like 'develop'), but include dirty info.
udmi_version=$(cd $UDMI_ROOT; git describe --dirty --match $VERSION_BASE) || true
# No luck... just generate any viable version.
[[ -n $udmi_version ]] || udmi_version=git-$(cd $UDMI_ROOT; git describe --dirty --match $VERSION_BASE --always) || true
[[ $udmi_version == git- ]] && udmi_version=unknown

# No dirty so it always will match. No shenanigans.
udmi_rev=$(cd $UDMI_ROOT; git describe --match $VERSION_BASE) || udmi_rev=unknown
revparse=`git rev-parse $udmi_rev` || revparse=unknown

udmi_commit=${revparse:0:9}
udmi_timever=$(TZ=UTC git log --date=iso-strict-local -1 --pretty=format:"%cd" ${udmi_commit}) || udmi_timever=unknown

export UDMI_ROOT
export UDMI_VERSION=$udmi_version
export UDMI_COMMIT=$udmi_commit
export UDMI_TIMEVER=$udmi_timever
export UDMI_REV=$udmi_rev

function stream_to_gsheets {
    local tool_name=$1
    local sheet_id=$2
    timestamp=$(date +%Y%m%d_%H%M%S)
    java -cp "$SERVICES_JAR" "com.google.udmi.util.SheetsOutputStream" "$tool_name" \
        "$sheet_id" "$tool_name.$timestamp.log"
}

function sync_disk_site_model_to_bambi {
    local spreadsheet_id=$1
    local local_path_to_site_model=$2
    java -cp "$SERVICES_JAR" "com.google.bos.iot.core.bambi.BambiSync" \
        "$spreadsheet_id" "$local_path_to_site_model"
}

function sync_bambi_site_model_to_disk {
    local spreadsheet_id=$1
    local local_path_to_site_model=$2
    java -cp "$SERVICES_JAR" "com.google.bos.iot.core.bambi.LocalDiskSync" \
        "$spreadsheet_id" "$local_path_to_site_model"
}

function clone_gcsr_site_model {
    local repo_name=$1
    local project_id=$2
    local clone_dir=$3
    echo Cloning "$repo_name" into "$clone_dir"
    rm -rf "$clone_dir"
    if gcloud source repos clone "$repo_name" "$clone_dir" --project="$project_id"; then
        echo "Successfully cloned '$repo_name' into '$clone_dir'"
        return 0
    else
        echo "Error cloning '$repo_name' into '$clone_dir'" >&2
        return 1
    fi
}

function setup_service_gcp_environment {
    local service_name="$1"
    echo "Configuring for GCP environment..."
    local service_account
    service_account=$(curl -s -H "Metadata-Flavor: Google" http://169.254.169.254/computeMetadata/v1/instance/service-accounts/default/email)

    echo "Using GCP service account: $service_account"
    gcloud config set account "$service_account"

    git config --global user.email "$service_account"
    git config --global user.name "${service_name} backend runner ${UDMI_PREFIX#\~}"
}

function build_services_jar {
    if [[ -d "$UDMI_ROOT/services/src" ]]; then
        echo "Checking for jar updates..."
        if ! up_to_date "$SERVICES_JAR" "$UDMI_ROOT/services/src"; then
            "$UDMI_ROOT/services/bin/build"
        fi
    fi
}

function ensure_pubsub_topic_exists {
    local topic_name="$1"

    echo "Checking for Pub/Sub topic: $topic_name"
    if ! gcloud pubsub topics describe "$topic_name" &>/dev/null; then
        echo "Creating topic: $topic_name"
        gcloud pubsub topics create "$topic_name"
    else
        echo "Topic $topic_name already exists."
    fi
}

function ensure_pubsub_subscription_exists {
    local subscription_name="$1"
    local topic_name="$2"
    local no_expiration_flag="${3:-}"

    echo "Checking for Pub/Sub subscription '$subscription_name'..."

    if ! gcloud pubsub subscriptions describe "$subscription_name" &>/dev/null; then
        echo "Creating subscription: $subscription_name"

        local gcloud_args=(
            "create" "$subscription_name"
            "--topic=$topic_name"
        )

        if [[ "$no_expiration_flag" != "--no-expiration" ]]; then
            echo "Setting a 24h expiration period."
            gcloud_args+=("--expiration-period=24h")
        else
            echo "Flag '--no-expiration' used. Creating without an expiration period."
        fi

        gcloud pubsub subscriptions "${gcloud_args[@]}"
    else
        echo "Subscription '$subscription_name' already exists."
    fi
}