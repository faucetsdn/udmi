"""
Script to generate presentation config from presentation attributes
defined in schema.
"""
import json
import os
import fnmatch
import shutil
from collections import OrderedDict, defaultdict

# --- Configuration ---
SCHEMA_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'schema')
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)),
                          'gencode', 'presentation')

# --- Constants ---
PRESENTATION = '$presentation'
DEFAULT_SECTION_KEY = '$defaultPresentation'
PRESENTATION_PROPS = 'presentationProperties'
REFERENCE = '$ref'
CURRENT = '$this'
PATHS = 'paths'
SECTION = 'section'
LABEL = 'label'
SCHEMA_KEY = 'schemaKey'
EXPECTED_TYPE = 'expectedType'
DESCRIPTION = 'description'
EXAMPLES = 'examples'
FORMAT = 'format'
EXPECTED_FORMAT = 'expectedFormat'


# --- Helper Functions ---


def load_json_file(filename, base_dir=SCHEMA_DIR):
  path = os.path.join(base_dir, filename)
  if not os.path.exists(path):
    print(f"Warning: Schema file not found: {path}")
    return None
  try:
    with open(path, 'r', encoding='utf-8') as f:
      return json.load(f)
  except json.JSONDecodeError as e:
    print(f"Error decoding JSON from {path}: {e}")
    return None


def find_presentation_properties(schema, path_prefix, collected_properties,
    origin_file_ref, default_config=None):
  """
  Recursively processes a schema, collecting properties with
  '$presentation' tags.
  """
  if not isinstance(schema, dict):
    return

  process_property(schema, path_prefix, collected_properties, origin_file_ref,
                   default_config)

  for key, prop_details in schema.get('properties', {}).items():
    if not isinstance(prop_details, dict):
      continue

    current_path = path_prefix + [key]
    find_presentation_properties(prop_details, current_path,
                                 collected_properties, origin_file_ref,
                                 default_config)


def process_property(prop_details, current_path, collected_properties,
    origin_file_ref, default_config=None):
  """
  Processes a single property. It first checks if the property itself should be
  added to the output, then handles recursion for nested structures.
  """
  full_path_str = '.'.join(current_path)
  presentation_config = prop_details.get(PRESENTATION)

  if len(current_path) > 1:
    is_opt_out = isinstance(presentation_config, dict) and not presentation_config
    if not is_opt_out:
      if isinstance(presentation_config, str):
        add_property_to_section(collected_properties, presentation_config,
                                full_path_str, prop_details)
      elif isinstance(presentation_config, dict):
        if 'paths' in presentation_config:
          presentation_paths = presentation_config.get(PATHS, {})
          parent_path_str = '.'.join(current_path[:-1])
          for required_path, details in presentation_paths.items():
            section_name, label = details.get(SECTION), details.get(LABEL)
            if not section_name: continue
            if (required_path == CURRENT and full_path_str.startswith(origin_file_ref)) or \
                (required_path != CURRENT and fnmatch.fnmatch(parent_path_str, required_path)):
              add_property_to_section(collected_properties, section_name,
                                      full_path_str, prop_details, label)
        elif 'label' in presentation_config:
          custom_label = presentation_config.get(LABEL)
          default_section = None
          if default_config:
            if isinstance(default_config, str):
              default_section = default_config
            elif isinstance(default_config, dict):
              default_paths = default_config.get(PATHS, {})
              parent_path_str = '.'.join(current_path[:-1])
              for required_path, details in default_paths.items():
                if fnmatch.fnmatch(parent_path_str, required_path):
                  default_section = details.get(SECTION)
                  break
          if default_section:
            add_property_to_section(collected_properties, default_section,
                                    full_path_str, prop_details, custom_label)

      elif presentation_config is None and default_config:
        is_leaf_node = 'properties' not in prop_details and '$ref' not in prop_details
        if is_leaf_node:
          if isinstance(default_config, str):
            if full_path_str.startswith(origin_file_ref):
              add_property_to_section(collected_properties, default_config,
                                      full_path_str, prop_details)
          elif isinstance(default_config, dict):
            default_paths = default_config.get(PATHS, {})
            parent_path_str = '.'.join(current_path[:-1])
            for required_path, details in default_paths.items():
              section_name, label = details.get(SECTION), details.get(LABEL)
              if not section_name: continue
              if fnmatch.fnmatch(parent_path_str, required_path):
                add_property_to_section(collected_properties, section_name,
                                        full_path_str, prop_details, label)
                break

  if isinstance(presentation_config, dict):
    if presentation_props := presentation_config.get(PRESENTATION_PROPS):
      for key, value_schema in presentation_props.items():
        specific_path = current_path + [key]
        find_presentation_properties(value_schema, specific_path,
                                     collected_properties, origin_file_ref,
                                     default_config)
  if ref_value := prop_details.get(REFERENCE):
    if ref_value.startswith('file:'):
      ref_path, _, ref_pointer_str = ref_value.partition('#')
      ref_filename = ref_path.split(':')[1]
      ref_schema = load_json_file(ref_filename)
      if ref_schema:
        new_default_config = ref_schema.get(DEFAULT_SECTION_KEY)

        if ref_pointer_str:
          try:
            for part in ref_pointer_str.strip('/').split('/'):
              ref_schema = ref_schema[part]
          except (KeyError, TypeError): ref_schema = None

        if ref_schema:
          new_origin_ref = ref_filename.replace('.json', '')
          find_presentation_properties(ref_schema, current_path,
                                       collected_properties,
                                       new_origin_ref,
                                       new_default_config)


def add_property_to_section(collected_properties, section_name, schema_key,
    prop_details, label=None):
  """
  Helper function to create and add a field entry.
  """
  if schema_key in collected_properties[section_name]:
    return

  fallback_label = schema_key[schema_key.find('.') + 1:]
  field_info = {
      LABEL: label or fallback_label,
      EXPECTED_TYPE: prop_details.get('type', 'string')
  }
  if 'type' in prop_details:
    field_info[EXPECTED_TYPE] = prop_details['type']

  if prop_details.get(DESCRIPTION):
    field_info[DESCRIPTION] = prop_details[DESCRIPTION]
  if prop_details.get(EXAMPLES):
    field_info[EXAMPLES] = str(prop_details[EXAMPLES])
  if FORMAT in prop_details:
    field_info[EXPECTED_FORMAT] = prop_details[FORMAT]

  collected_properties[section_name][schema_key] = field_info


def generate_view_files():
  if not os.path.exists(SCHEMA_DIR):
    print(f"Error: Schema directory not found at '{SCHEMA_DIR}'")
    return
  if not os.path.exists(OUTPUT_DIR):
    os.makedirs(OUTPUT_DIR)
    print(f"Created output directory: {OUTPUT_DIR}")

  shutil.rmtree(OUTPUT_DIR)
  os.makedirs(OUTPUT_DIR)
  collected_properties = defaultdict(OrderedDict)
  schema_files = [x for x in os.listdir(SCHEMA_DIR) if
                  os.path.isfile(os.path.join(SCHEMA_DIR, x))]
  for filename in schema_files:
    print(f"Scanning root schema: {filename}")
    schema = load_json_file(filename)
    if not schema:
      continue

    default_config = schema.get(DEFAULT_SECTION_KEY)

    origin_file_ref = filename.replace('.json', '')
    path_prefix = [origin_file_ref]
    find_presentation_properties(schema, path_prefix, collected_properties,
                                 origin_file_ref, default_config)

  print(f"\nFound {len(collected_properties)} sections. "
        f"Generating presentation files...")

  output_path = os.path.join(OUTPUT_DIR, 'presentation.json')
  with open(output_path, 'w', encoding='utf-8') as f:
    json.dump(collected_properties, f, indent=2)

  print(f"\nSuccessfully generated presentation configuration file at "
        f"'{output_path}/'")


if __name__ == '__main__':
  generate_view_files()