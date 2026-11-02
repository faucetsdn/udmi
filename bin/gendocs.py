#!/usr/bin/env python3
""" Generate HTML documentation for JSON Schema """
import sys

from json_schema_for_humans.generate import generate_from_filename
from json_schema_for_humans.generation_configuration import GenerationConfiguration
import json_schema_for_humans.schema.intermediate_representation as ir
from json_schema_for_humans.schema.schema_node import SchemaNode

# pylint: disable=protected-access,invalid-name
orig_has_ref_to_parent = ir._has_reference_to_parent
orig_node_is_parent = SchemaNode.node_is_parent


def _has_reference_to_parent(referenced_schema_path, anchor_part, current_node):
  node = current_node
  while node:
    if orig_has_ref_to_parent(referenced_schema_path, anchor_part, node):
      return True
    node = node.parent
  return False


def _node_is_parent(self, node_to_check):
  node = self
  while node:
    if orig_node_is_parent(node, node_to_check):
      return True
    node = node.parent
  return False


ir._has_reference_to_parent = _has_reference_to_parent
SchemaNode.node_is_parent = _node_is_parent

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
