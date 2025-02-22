import argparse
import re
import sys


def parse_command_line_args():
  parser = argparse.ArgumentParser()

  parser.add_argument('master_file', type=str, help='master file to merge into')

  parser.add_argument('report_file', type=str, help='report file to merge')

  return parser.parse_args()


def main():
  args = parse_command_line_args()
  master_path = args.master_file
  report_path = args.report_file

  with open(report_path, 'r', encoding='utf-8') as f:
    report_contents = f.read()

  table = re.compile(
      r'(?sm)<!-- START: ([--_/A-Za-z0-9]*) -->(.*)<!-- END:'
      r' [--_/A-Za-z0-9]* -->'
  )
  matches = re.search(table, report_contents)

  if matches:
    device_path = matches.group(1)
    report = matches[0]
  else:
    print('could not find results in report')
    sys.exit(1)

  with open(master_path, 'r', encoding='utf-8') as f:
    master_contents = f.read()

  if re.search(rf'<!-- START: {device_path}', master_contents):
    master_contents = re.sub(
        rf'(?sm)<!-- START: {device_path} -->(.*)<!-- END: {device_path} -->',
        report,
        master_contents,
    )
  else:
    master_contents = re.sub('</body>', f'{report}\n</body>', master_contents)

  with open(master_path, 'w', encoding='utf-8') as f:
    f.write(master_contents)


if __name__ == '__main__':
  main()
