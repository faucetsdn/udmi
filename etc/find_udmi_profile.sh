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
        udmi_site=$(jq -r .site_model < $1)
        profile_dir=$(dirname $1)
        if [[ -z ${udmi_site} ]]; then
            udmi_site=$(cd ${profile_dir}; find_site_model_root)
        elif [[ ${udmi_site} == "null" ]]; then
            udmi_site=
        elif [[ ${udmi_site} =~ ^/ ]]; then
            true # model is absolue, so don't munge.
        else
            udmi_site=$(realpath --relative-base ${PWD} ${profile_dir}/${udmi_site})
        fi
        echo ${udmi_site}
    fi
}

udmi_site=$(find_site_model_root)
udmi_profile=

if [[ -z $1 || $1 =~ ^- ]]; then
    # No argument specified
    if [[ -d ${udmi_site}/.udmi ]]; then
        udmi_profile=${udmi_site}/.udmi/default_profile.json
        echo Using site model default udmi profile $udmi_profile
    else
        udmi_profile=~/.udmi/default_profile.json
        echo Using user default udmi profile $udmi_profile
    fi
    if [[ ! -f ${udmi_profile} ]]; then
        echo Creating empty default profile ${udmi_profile}
        mkdir -p $(dirname ${udmi_profile})
        echo {} > ${udmi_profile}
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
    if [[ -n ${udmi_site} ]]; then
        udmi_profile=${udmi_site}/.udmi/profile_${profile_name}.json
        alt_profile="or $udmi_profile"
    fi
    if [[ ! -f ${udmi_profile} ]]; then
        udmi_profile=~/.udmi/profile_${profile_name}.json
    fi
    if [[ ! -f ${udmi_profile} ]]; then
        echo Missing named udmi profile $udmi_profile $alt_profile
        udmi_profile=
        false
    else
        echo Using named udmi profile $udmi_profile
    fi
fi

udmi_profile=$(realpath --relative-base $PWD ${udmi_profile})

profile_udmi_site=$(find_or_extract $udmi_profile)
if [[ -n $profile_udmi_site ]]; then
    udmi_site=$profile_udmi_site
    echo Using extracted site model $udmi_site
elif [[ -n $udmi_site ]]; then
    echo Using implicit site model $udmi_site
fi

if [[ -n $udmi_site ]]; then
    udmi_site=$(realpath --relative-base $PWD ${udmi_site})
else
    udmi_site=null
    echo No implicit or explicit site model found.
fi
