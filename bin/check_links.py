"""
Checks that inline markdown links in documentation and schema are valid

Prints any invalid links, otherwise quiet if all links are valid
"""
import glob
import re
import os


def split_link_fragment(link):
    """ Splits a link into the path and the fragment.
        e.g. file.md#header is split into `file.md` and `#header`

        Arguments
            Link 

        Returns tuple:
            file_path   
            anchor      None if not defined
    """
    matches = re.search(r'^([^#]*)(#.*)?$', link)
    if not matches:
        raise ValueError
    return matches.groups()


def check_links(file_path):
    """
        Checks if inline markdown links within the given file_path
        resolve

        Arguments:
            file_path:  file to check links within

        Returns:
            list of links which did not resolve
    """

    failing_links = []

    with open(file_path, 'r') as f:
        links = re.findall(r'\[.*\]\(([^\)]*)\)', f.read())
        for link in links:
            if not re.match('https?://', link):
                link_path, link_anchor = split_link_fragment(link)
                dir_name = os.path.dirname(os.path.realpath(file_path))

                # Links are relative to the file they were found in 
                resolved_path = os.path.realpath(os.path.join(dir_name, link_path))

                if not os.path.exists(resolved_path):
                    failing_links.append(link)
    
    return failing_links


if __name__ == "__main__":

    check_paths = ['**/*.md', 'schema/*.json']
    for check_path in check_paths:
        file_paths = glob.glob(check_path, recursive=True)
        for file_path in file_paths:
            invalid_links = check_links(file_path)
            if invalid_links:
                for invalid_link in invalid_links:
                    print(f'** {invalid_link}')
