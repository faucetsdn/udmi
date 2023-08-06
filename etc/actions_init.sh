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

UDMI_WORKFLOW=$HOME/udmi/etc/site_model_workflow.yaml
MODEL_WORKFLOW=.github/workflows/testing.yaml

pwd
ls -la
find . -name testing.yaml
ls -l $UDMI_WORKFLOW $MODEL_WORKFLOW
! diff $UDMI_WORKFLOW $MODEL_WORKFLOW || fail UDMI and Model workflows differ
