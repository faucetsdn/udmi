import argparse
import os
import sys
from device_runs import DeviceRuns
import jinja2
from template_helper import TemplateHelper


def parse_command_line_args():
  parser = argparse.ArgumentParser()

  parser.add_argument('results_dir', type=str, help='path to site model')

  parser.add_argument('device_path', type=str, help='device_path')

  parser.add_argument('report_path', type=str, help='file to save report')

  return parser.parse_args()


def main():
  args = parse_command_line_args()
  results_dir = args.results_dir
  output_file = args.report_path
  device_path = args.device_path

  device_runs = DeviceRuns(results_dir)
  results = device_runs.test_matrix

  environment = jinja2.Environment()
  environment.filters['result_background'] = TemplateHelper.result_background
  environment.filters['commit_background'] = TemplateHelper.commit_background
  environment.filters['header_date'] = TemplateHelper.header_date

  gcs_bucket = f"{os.environ['GCS_BUCKET']}/{os.environ['GCS_RESULTS_SUBDIR']}"
  print(gcs_bucket)
  with open(os.path.join(sys.path[0], 'device_template.html')) as f:
    template = environment.from_string(f.read())

  output = template.render(
      results=results, runs=device_runs.runs, device_path=device_path, gcs_bucket=gcs_bucket
  )

  with open(output_file, 'w') as f:
    f.write(output)


if __name__ == '__main__':
  main()
