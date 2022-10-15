#
# General utility functions for working with UDMI scripts. Will be source'd by
# executing scripts.
#


#
# Walk up the directory tree looking for the .udmi configuration directory.
# If not found, just return empty.
#
function find_project_root {
    while [ $PWD != "/" ]; do
        test -e .udmi && { pwd; break; }
        cd ..
    done
}
