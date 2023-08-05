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
