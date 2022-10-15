#
# Look through and find the project root and profile to use for tools.
# This is not a function somewhere because it needs to manipulate the argument stack.
#
# A few configuration cases are supported:
#  1. No argument, or first argument starts with -. Look for working project directory and use default profile.
#  2. Specific profile specified as file wiht .json extension. Working project derrived from profile path.
#  3. Semantir profile specificed (no .json). Working project derrived from current working directory.

profile=.udmi/default_profile.json
if [[ -n $1 && ! $1 =~ ^- ]]; then
    if [[ $1 =~ .json$ ]]; then
        echo Using project profile $1
        project=`cd $(dirname $1); find_project_root`
        if [[ -n $project ]]; then
            # Target profile is underneath a .udmi project diretory.
            echo Found project root $project
            profile=$(realpath $1 --relative-to $project)
        else
            # Target profile is standalone, so defer working directory lookup.
            profile=$1
        fi
    else
        echo Targetting profile $1
        profile=.udmi/profile_$1.json
    fi
    shift
fi

if [[ -z $project ]]; then
    echo Loooking for current working project root...
    project=`find_project_root`
    if [[ -z $project ]]; then
        echo No project root .udmi config directory found
        false
    fi
    echo Found udmi project root $project
fi

project_profile=${project}/${profile}

if [[ ! -f $project_profile ]]; then
    echo Project profile $project_profile not found.
    false
fi

echo $project_profile
