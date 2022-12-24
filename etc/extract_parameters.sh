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

while getopts "d:p:x:" opt; do
    case $opt in
        d)
            udmi_device=${OPTARG}
            ;;
        p)
            udmi_project=${OPTARG}
            ;;
        x)
            udmi_serial=${OPTARG}
            ;;
        \?)
            echo "Usage: [-d device] [-p project] [-x serial]"
            false
            ;;
    esac
done

shift $((OPTIND-1))
