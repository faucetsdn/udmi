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
PRESENTATION = "$presentation"
PRESENTATION_PROPS = "presentationProperties"
REFERENCE = "$ref"
CURRENT = "$this"
PATHS = "paths"
SECTION = "section"
LABEL = "label"
SCHEMA_KEY = "schemaKey"
EXPECTED_TYPE = "expectedType"
DESCRIPTION = "description"
EXAMPLES = "examples"
FORMAT = "format"
EXPECTED_FORMAT = "expectedFormat"

# --- Helper Functions ---


def load_json_file(filename, base_dir=SCHEMA_DIR):
    path = os.path.join(base_dir, filename)
    if not os.path.exists(path):
        print(f"Warning: Schema file not found: {path}")
        return None
    try:
        with open(path, 'r') as f:
            return json.load(f)
    except json.JSONDecodeError as e:
        print(f"Error decoding JSON from {path}: {e}")
        return None


def find_presentation_properties(schema, path_prefix, collected_properties,
                                 origin_file_ref):
    """
    Recursively processes a schema, collecting properties with
    '$presentation' tags.
    """
    if not isinstance(schema, dict):
        return

    # Process properties defined at the current level
    process_property(schema, path_prefix, collected_properties, origin_file_ref)

    # Recurse into any nested properties
    for key, prop_details in schema.get('properties', {}).items():
        if not isinstance(prop_details, dict):
            continue

        current_path = path_prefix + [key]
        find_presentation_properties(prop_details, current_path,
                                     collected_properties, origin_file_ref)


def process_property(prop_details, current_path, collected_properties,
                     origin_file_ref):
    """
    Processes a single property, handling refs, presentationProperties, and
    standard presentation tags.
    """
    # 1. Handle `presentationProperties` for map-like objects.
    if presentation_props := prop_details.get(PRESENTATION,
                                              {}).get(PRESENTATION_PROPS):
        for key, value_schema in presentation_props.items():
            specific_path = current_path + [key]
            find_presentation_properties(value_schema, specific_path,
                                         collected_properties, origin_file_ref)

    # 2. Resolve a standard $ref if it exists.
    if ref_value := prop_details.get(REFERENCE):
        if ref_value.startswith('file:'):
            ref_path, _, ref_pointer_str = ref_value.partition('#')
            ref_filename = ref_path.split(':')[1]
            if ref_schema := load_json_file(ref_filename):
                if ref_pointer_str:
                    try:
                        for part in ref_pointer_str.strip('/').split('/'):
                            ref_schema = ref_schema[part]
                    except (KeyError, TypeError):
                        ref_schema = None
                if ref_schema:
                    new_origin_ref = ref_filename.replace('.json', '')
                    find_presentation_properties(ref_schema, current_path,
                                                 collected_properties,
                                                 new_origin_ref)

    # 3. Check for a standard presentation tag on the property itself.
    if PRESENTATION in prop_details:
        parent_path_str = '.'.join(current_path[:-1]) if len(
            current_path) > 1 else ''
        full_path_str = '.'.join(current_path)
        presentation_paths = prop_details[PRESENTATION].get(PATHS, {})
        for required_path, details in presentation_paths.items():
            section_name, label = details.get(SECTION), details.get(LABEL)
            if not section_name:
                continue

            if required_path == CURRENT and \
                    full_path_str.startswith(origin_file_ref):
                add_property_to_section(collected_properties, section_name,
                                        full_path_str, prop_details, label)
                break
            elif required_path != CURRENT and fnmatch.fnmatch(parent_path_str,
                                                              required_path):
                add_property_to_section(collected_properties, section_name,
                                        full_path_str, prop_details, label)
                break


def add_property_to_section(collected_properties, section_name, schema_key,
                            prop_details, label=None):
    """
    Helper function to create and add a field entry.
    """
    if schema_key in collected_properties[section_name]:
        return

    field_info = {
            LABEL: label or schema_key[schema_key.find('.') + 1:],
            EXPECTED_TYPE: prop_details.get('type', 'string')
    }
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

        origin_file_ref = filename.replace('.json', '')
        path_prefix = [origin_file_ref]
        find_presentation_properties(schema, path_prefix, collected_properties,
                                     origin_file_ref)

    print(f"\nFound {len(collected_properties)} sections. "
          f"Generating presentation files...")

    output_path = os.path.join(OUTPUT_DIR, f"presentation.json")
    with open(output_path, 'w') as f:
        json.dump(collected_properties, f, indent=2)

    print(f"\nSuccessfully generated presentation configuration file at "
          f"'{output_path}/'")


if __name__ == '__main__':
    generate_view_files()