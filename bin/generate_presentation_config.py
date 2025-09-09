"""
Script to generate presentation config from presentation attributes
defined in schema.
"""
import json
import os
import re

SCHEMA_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'schema')
ROOT_CONFIG_FILE = os.path.join(os.path.dirname(os.path.dirname(__file__)),
                                'docs', 'guides', 'presentation_layer.md')
OUTPUT_FILE = os.path.join(os.path.dirname(os.path.dirname(__file__)),
                           'gencode', 'presentation', 'presentation.json')


def get_root_schemas(file_path):
  """
  Parses the Markdown file to find the JSON code block
  and extract the list of root schema filenames.
  """
  print(f"Reading root schemas from {file_path}...")
  try:
    with open(file_path, 'r') as f:
      content = f.read()
      json_block_match = re.search(r'```json\s*([\s\S]*?)\s*```', content)
      if not json_block_match:
        raise ValueError("No JSON code block found in the markdown file.")

      json_content = json.loads(json_block_match.group(1))
      roots = json_content.get("roots")
      if not roots or not isinstance(roots, list):
        raise ValueError("JSON block must contain a 'roots' array.")

      print(f"Found root schemas: {', '.join(roots)}")
      return roots
  except FileNotFoundError:
    print(f"Error: {file_path} not found.")
    exit(1)
  except (json.JSONDecodeError, ValueError) as e:
    print(f"Error parsing {file_path}: {e}")
    exit(1)


def load_all_schemas(schema_dir):
  """
  Loads all .json files from the schema directory into a dictionary.
  """
  print(f"Loading all schemas from directory '{schema_dir}'...")
  all_schemas = {}
  try:
    for filename in os.listdir(schema_dir):
      if filename.endswith('.json'):
        file_path = os.path.join(schema_dir, filename)
        with open(file_path, 'r') as f:
          all_schemas[filename] = json.load(f)
    print(f"Successfully loaded {len(all_schemas)} schemas.")
    return all_schemas
  except FileNotFoundError:
    print(f"Error: Schema directory '{schema_dir}' not found.")
    exit(1)


def traverse_schema(schema_obj, all_schemas, path_prefix="",
    inherited_section="", force_hide=False,
    injected_config=None):
  """
  Recursively traverses a schema object and its references to build a
  flattened presentation configuration.
  """
  if not schema_obj:
    return {}

  presentation_config = {}
  fields_to_capture = ['type', 'description', 'examples']

  # This handler is for when the schema_obj *itself* is a map defined
  # by patternProperties, and its parent is injecting configuration.
  if ('patternProperties' in schema_obj and
      '$presentation' in schema_obj and 'paths' in schema_obj['$presentation']):

    paths_obj = schema_obj['$presentation'].get('paths', {})
    if not isinstance(paths_obj, dict):
      print(
          f"Warning: $presentation.paths for {path_prefix} is not an object. "
          f"Skipping.")
      return {}

    # Get the generic schema definition for the pattern (it's probably a $ref)
    pattern_schema_def = next(iter(schema_obj['patternProperties'].values()))

    # Resolve the $ref, exactly like we do in the main properties loop
    resolved_pattern_schema = pattern_schema_def
    if '$ref' in pattern_schema_def:
      ref_string = pattern_schema_def['$ref'].replace('file:', '')
      if '#' in ref_string:
        ref_name, json_pointer = ref_string.split('#', 1)
      else:
        ref_name, json_pointer = ref_string, None

      resolved_base = all_schemas.get(ref_name)
      if not resolved_base:
        raise FileNotFoundError(
            f"Schema file '{ref_name}' not found for ref '{ref_string}'.")

      if json_pointer:
        path_parts = json_pointer.strip('/').split('/')
        resolved_pointer = resolved_base
        for part in path_parts:
          resolved_pointer = resolved_pointer.get(part)
          if resolved_pointer is None:
            raise ValueError(
                f"JSON pointer path '{json_pointer}' not found in {ref_name}.")
        resolved = resolved_pointer
      else:
        resolved = resolved_base

      # Combine the resolved base schema with any local overrides from the
      # $ref object
      resolved_pattern_schema = {**resolved, **pattern_schema_def}

    # Now, iterate the path *object* (key and config) and RECURSE
    for path_key, path_config in paths_obj.items():
      current_path = f"{path_prefix}.{path_key}" if path_prefix else path_key

      # Recurse into the resolved schema (e.g., model_localnet_family.json)
      # and pass the config (e.g., {"adjunct.paths":...}) as the new
      # injected_config
      child_config = traverse_schema(
          resolved_pattern_schema,
          all_schemas,
          current_path,
          inherited_section,
          force_hide,
          injected_config=path_config
      )
      presentation_config.update(child_config)

    # We have fully processed this schema object. Return the results
    # and prevent falling through to the 'properties' handler below.
    return presentation_config

  # Handle regular properties.
  if 'properties' in schema_obj:
    for key, prop_schema in schema_obj['properties'].items():
      current_path = f"{path_prefix}.{key}" if path_prefix else key

      effective_prop_schema = prop_schema
      if '$ref' in prop_schema:
        ref_string = prop_schema['$ref'].replace('file:', '')
        if '#' in ref_string:
          ref_name, json_pointer = ref_string.split('#', 1)
        else:
          ref_name, json_pointer = ref_string, None

        resolved_base = all_schemas.get(ref_name)
        if not resolved_base:
          raise FileNotFoundError(
              f"Schema file '{ref_name}' not found for ref '{ref_string}'.")

        if json_pointer:
          path_parts = json_pointer.strip('/').split('/')
          resolved_pointer = resolved_base
          for part in path_parts:
            resolved_pointer = resolved_pointer.get(part)
            if resolved_pointer is None:
              raise ValueError(
                  f"JSON pointer path '{json_pointer}' not "
                  f"found in {ref_name}.")
          resolved = resolved_pointer
        else:
          resolved = resolved_base
        effective_prop_schema = {**resolved, **prop_schema}

      current_presentation = prop_schema.get('$presentation', {})
      section_for_this_level = current_presentation.get('section',
                                                        inherited_section)

      new_force_hide = force_hide or current_presentation.get(
          'display') == 'hide'

      # Check if the injected_config (from the parent) has instructions
      # for this specific property key.
      injected_key_name = f"{key}.paths"  # e.g., "adjunct.paths"
      if injected_config and injected_key_name in injected_config:

        injected_paths_obj = injected_config[injected_key_name]

        if 'patternProperties' not in effective_prop_schema:
          print(
              f"Warning: Injected paths found for '{key}', "
              f"but no patternProperties in its schema.")
          continue

        # Get the generic schema for the items in this map (e.g., the base
        # "string" schema)
        pattern_schema = next(
            iter(effective_prop_schema['patternProperties'].values()))

        # Loop over the *injected keys* (e.g., "name", "serial_port")
        for injected_key, injected_prop_config in injected_paths_obj.items():
          # injected_key = "name"
          # injected_prop_config = { "style": "bold", "description": "..." }

          # e.g., "families.bacnet.adjunct.name"
          injected_path = f"{current_path}.{injected_key}"

          # Start building the config from the injected data
          final_prop_config = injected_prop_config.copy()
          final_prop_config[
            'display'] = 'show'  # If it's injected, we must show it
          final_prop_config[
            'section'] = section_for_this_level  # Inherit section

          # Add data from the generic pattern_schema (like 'type')
          # if it wasn't already specified in the injected config
          for field in fields_to_capture:
            if field in pattern_schema and field not in final_prop_config:
              final_prop_config[field] = pattern_schema[field]

          if not force_hide:
            presentation_config[injected_path] = final_prop_config

        # We have manually processed this property and its injected children.
        # Do NOT recurse or process it further.
        continue

      if effective_prop_schema.get(
          'type') == 'object' or effective_prop_schema.get('existingJavaType',
                                                           None):
        child_config = traverse_schema(effective_prop_schema, all_schemas,
                                       current_path, section_for_this_level,
                                       new_force_hide,
                                       injected_config=injected_config)
        presentation_config.update(child_config)

      if current_presentation.get('display') == 'show' and not force_hide:
        prop_config = current_presentation.copy()
        prop_config['section'] = section_for_this_level

        for field in fields_to_capture:
          if field in effective_prop_schema:
            prop_config[field] = effective_prop_schema[field]

        presentation_config[current_path] = prop_config

  return presentation_config


def main():
  """
  Main function to drive the presentation config generation.
  (This function is unchanged)
  """
  root_schemas = get_root_schemas(ROOT_CONFIG_FILE)
  all_schemas = load_all_schemas(SCHEMA_DIR)

  final_config = {}

  for root in root_schemas:
    print(f"Processing root schema: {root}...")
    root_schema_obj = all_schemas.get(root)
    if not root_schema_obj:
      print(f"Warning: Root schema {root} not found. Skipping.")
      continue

    presentation_info = root_schema_obj.get('$presentation', {})
    root_section = presentation_info.get('section', root.replace('.json', ''))

    properties = traverse_schema(root_schema_obj, all_schemas,
                                 inherited_section=root_section)
    final_config[root] = properties

  print(f"Writing final configuration to {OUTPUT_FILE}...")
  with open(OUTPUT_FILE, 'w') as f:
    json.dump(final_config, f, indent=2)

  print("Done.")


if __name__ == '__main__':
  main()
