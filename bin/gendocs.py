#!/usr/bin/env python3
""" Generate HTML documentation for JSON Schema """
import sys

from json_schema_for_humans.generate import generate_from_filename
from json_schema_for_humans.generation_configuration import GenerationConfiguration

config = GenerationConfiguration(copy_css=True,
                                 expand_buttons=True,
                                 minify=False,
                                 link_to_reused_ref=False,
                                 footer_show_time=False)

schema = sys.argv[1]
source_dir = sys.argv[2]
output_dir = sys.argv[3]

schema_path = source_dir + "/" + schema + ".json"
output_path = output_dir + "/" + schema + ".html"

generate_from_filename(schema_path, output_path, config=config)
