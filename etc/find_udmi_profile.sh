#
# Look through and find the site and profile to use for tools.
# This is not a function somewhere because it needs to manipulate the argument stack.
#
# A few configuration cases are supported:
#  1. No argument, or first argument starts with -
#    a. Look for working site_model directory and use ${site_mode}/.udmi/defailt_profile.json
#    b. Look in ~/.udmi/default_profile.json
#  2. Specific profile specified as file wiht .json extension
#    a. Site defined in profile
#    a. Site derived from working path (parent with .udmi directory)
#  3. Semantic profile specificed (no .json)
#    a. Working site derived from current working directory, look in ${site_mode}/.udmi/profile_${profile}.json
#    b. Look in ~/.udmi/profile_${profile}.json
#

function find_or_extract {
    if [[ -f $1 ]]; then
        site_model=$(jq -r .site_model < $1)
        if [[ -z ${site_model} ]]; then
            site_model=$(cd $(dirname $1); find_site_model_root)
        elif [[ ${site_model} == "null" ]]; then
            site_model=
        fi
        echo ${site_model}
    fi
}

udmi_profile=
if [[ -z $1 || $1 =~ ^- ]]; then
    # No argument specified
    site_model=$(find_site_model_root)
    if [[ -d ${site_model}/.udmi ]]; then
        echo Using working site model $site_model
        udmi_profile=${site_model}/.udmi/default_profile.json
        echo Using site model default udmi profile $udmi_profile
    else
        udmi_profile=~/.udmi/default_profile.json
        echo Using user default udmi profile $udmi_profile
        site_model=$(find_or_extract $udmi_profile)
        echo Extracted site model $site_model
    fi
elif [[ $1 =~ .json$ ]]; then
    # Explicit .json file
    udmi_profile=$1
    shift
    site_model=$(find_or_extract $udmi_profile)
else
    # Semantic profile (no .json suffix)
    profile_name=$1
    shift
    site_model=$(find_site_model_root)
    if [[ ! -f ${site_model}/cloud_iot_config.json ]]; then
        site_model=
    fi
    if [[ -n ${site_model} ]]; then
        udmi_profile=${site_model}/.udmi/profile_${profile_name}.json
    else
        udmi_profile=~/.udmi/profile_${profile_name}.json
        site_model=$(find_or_extract $udmi_profile)
    fi
    if [[ -z ${site_model} ]]; then
        udmi_profile=
    fi
fi

if [[ ! -f ${udmi_profile} ]]; then
    echo Invalid/empty udmi_profile ${udmi_profile}
    udmi_profile=
else
    udmi_profile=$(realpath --relative-base $HOME ${udmi_profile})
fi

if [[ ! -f ${site_model}/cloud_iot_config.json ]]; then
    echo Invalid/empty site_model ${site_model}
    site_model=
else
    site_model=$(realpath --relative-base $HOME ${site_model})
fi
