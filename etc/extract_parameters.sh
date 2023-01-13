#
# Assuming the profile has been previously extracted/defined, this script will parse out the common
# profile parameters and process any common command-line arguments.
#

if [[ -z $udmi_profile ]]; then
    echo udmi_profile not defined.
    false
fi

udmi_device=$(jq -r .device_id $udmi_profile)
udmi_project=$(jq -r .project_id $udmi_profile)
udmi_serial=$(jq -r .serial_no $udmi_profile)
udmi_runsec=$(jq -r .run_sec $udmi_profile)

while getopts "p:d:x:l:s:" opt; do
    case $opt in
        p)
            udmi_project=${OPTARG}
            ;;
        s)
            udmi_site=${OPTARG}
            ;;
        d)
            udmi_device=${OPTARG}
            ;;
        x)
            udmi_serial=${OPTARG}
            ;;
        l)
            udmi_runsec=${OPTARG}
            ;;
        \?)
            echo "Usage: [-p project] [-s site] [-d device] [-x serial] [-l length]"
            false
            ;;
    esac
done

shift $((OPTIND-1))

function udmi_help {
    echo
    echo $*
    echo
    echo 'Common udmi profile paramaeters (or command line option):'
    echo '  project_id (-p): the cloud project id'
    echo '  site_model (-s): the udmi site model'
    echo '  device_id (-d): the device id to test/validate'
    echo '  serial_no (-x): the device serial number for test'
    echo '  run_sec (-l): the test run length in seconds'
    false
}
