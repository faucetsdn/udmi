from device_runs import DeviceRuns
import jinja2
from template_helper import TemplateHelper
import argparse
import os
import sys

def parse_command_line_args():
    parser = argparse.ArgumentParser()

    parser.add_argument('site_model', type=str,
                        help='path to site model')

    parser.add_argument('device_id', type=str,
                        help='device_path')

    return parser.parse_args()

def main():
    args = parse_command_line_args()
    site_model = args.site_model
    device_id = args.device_id
    
    results = sequencerResults
    results = device_runs.test_matrix

    environment = jinja2.Environment()
    environment.filters["result_background"] = TemplateHelper.result_background
    environment.filters["commit_background"] = TemplateHelper.commit_background
    environment.filters["header_date"] = TemplateHelper.header_date

    with open(os.path.join(sys.path[0], 'device_template.html')) as f:
        template = environment.from_string(f.read())

    output = template.render( \
        results=results, 
        runs=device_runs.runs, 
        device_path=device_path)

    with open(output_file, "w") as f:
        f.write(output)

if __name__ == "__main__":
    main()