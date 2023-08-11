echo Processing .github/udmi_init.sh

# Dummy function to make output processing nicer.
function steps {
    true
}

# Show message and fail.
function fail {
    echo $*
    false
}

export UDMI_BIN=$HOME/udmi/bin

UDMI_WORKFLOW=$HOME/udmi/etc/site_model_workflow.yml

export GCP_PROJECT=${PROJECT_SPEC##*/}
prefix=${PROJECT_SPEC%/*}
export IOT_PROVIDER=${prefix#//}
