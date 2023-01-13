#
# Look through and find the site and profile to use for tools.
# This is not a function somewhere because it needs to manipulate the argument stack.
#
# A few configuration cases are supported:
#  1. No argument, or first argument starts with -
#    a. Look for working site_model directory and use ${site_mode}/.udmi/defailt_profile.json
#    b. Look in ~/.udmi/default_profile.json
#  2. Specific profile specified as file wiht .json extension
#    a. Site derived from working path (parent with .udmi directory)
#    b. Site defined in profile
#  3. Semantic profile specificed (no .json)
#    a. Working site derived from current working directory, look in ${site_mode}/.udmi/profile_${profile}.json
#    b. Look in ~/.udmi/profile_${profile}.json
#

function find_or_extract {
    if [[ -f $1 ]]; then
        site_model=$(jq -r .site_model < $1)
        profile_dir=$(dirname $1)
        if [[ -z ${site_model} ]]; then
            site_model=$(cd ${profile_dir}; find_site_model_root)
        elif [[ ${site_model} == "null" ]]; then
            site_model=
        elif [[ ${site_model} =~ ^/ ]]; then
            true # model is absolue, so don't munge.
        else
            site_model=$(realpath --relative-base ${PWD} ${profile_dir}/${site_model})
        fi
        echo ${site_model}
    fi
}

site_model=$(find_site_model_root)
udmi_profile=

if [[ -z $1 || $1 =~ ^- ]]; then
    # No argument specified
    if [[ -d ${site_model}/.udmi ]]; then
        udmi_profile=${site_model}/.udmi/default_profile.json
        echo Using site model default udmi profile $udmi_profile
    else
        udmi_profile=~/.udmi/default_profile.json
        echo Using user default udmi profile $udmi_profile
    fi
elif [[ $1 =~ .json$ ]]; then
    # Explicit .json file
    udmi_profile=$1
    echo Using explicit udmi profile $udmi_profile
    shift
else
    # Semantic profile (no .json suffix)
    profile_name=$1
    shift
    if [[ -n ${site_model} ]]; then
        udmi_profile=${site_model}/.udmi/profile_${profile_name}.json
    else
        udmi_profile=~/.udmi/profile_${profile_name}.json
    fi
    echo Using named udmi profile $udmi_profile
fi

if [[ ! -f ${udmi_profile} ]]; then
    echo Creating empty profile ${udmi_profile}
    mkdir -p $(dirname ${udmi_profile})
    echo {} > ${udmi_profile}
fi
udmi_profile=$(realpath --relative-base $PWD ${udmi_profile})

profile_site_model=$(find_or_extract $udmi_profile)
if [[ -n $profile_site_model ]]; then
    site_model=$profile_site_model
    echo Using extracted site model $site_model
else
    echo Using implicit site model $site_model
fi

if [[ -z $site_model ]]; then
    echo No implicit or explicit site model found.
    false
fi
site_model=$(realpath --relative-base $PWD ${site_model})
