import sys

from json_schema_for_humans.generate import generate_from_filename
from json_schema_for_humans.generation_configuration import GenerationConfiguration

config = GenerationConfiguration(copy_css=True, 
                                 expand_buttons=True,
                                 minify=False)

schema = sys.argv[1]
root_dir = sys.argv[2]
output_dir = sys.argv[3]

file_name = root_dir + "/tmp/schema/" + schema + ".json"
output_file = root_dir + "/" + output_dir + "/" + schema + ".html"

generate_from_filename(file_name, output_file, config=config)