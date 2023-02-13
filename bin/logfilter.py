#!/usr/bin/python3
import re
import sys
import json
import getopt

RE_SEQUENCER_LINE = re.compile(
  '^(?P<date>\d{4}-\d{2}-\d{2})T'
  '(?P<time>\d\d:\d\d:\d\d)Z\s'
  '(?P<loglevel>[^\s]+)\s'
  '(?P<process>sequencer)\s+'
  '(?P<tail>.*)')

RE_PUBBER_LINE = re.compile(
  '^INFO daq.pubber.(?P<class>\w+) - '
  '(?P<date>\d{4}-\d{2}-\d{2})T'
  '(?P<time>\d\d:\d\d:\d\d)Z\s'
  '(?P<tail>.*)')

RE_SEQUENCER_TAIL_JSON_BEGIN = re.compile(
  '^Updated (?P<type>config|state) \#(?P<num>\d+):$')

RE_PUBBER_TAIL_JSON_BEGIN = re.compile(
  r'^(?P<type>Config|State) update(|\s+\(\w+\)):$')

json_stack = None

def fd_print(fd, *args):
  if len(args) == 1:
    print(args[0], file=fd)
  else:
    print(args[0] % args[1:], file=fd)

def output_print(*args):
  fd_print(sys.stdout, *args)

def debug_print(*args):
  fd_print(sys.stderr, *args)

def json_diff_print(json_obj1, json_obj2):
  from _pytest.assertion.util import _compare_eq_any
  output_print('======JSON WITH DIFF')
  output_print(json.dumps(json_obj2, indent=2)) 
  output_print('======DIFF BEGIN')
  output_print('\n'.join(_compare_eq_any(json_obj1, json_obj2, verbose=3)))
  output_print('======DIFF END')

def json_stack_print(json_type, json_num):
  global json_stack

  if (json_num-1) in json_stack[json_type]:
    json_diff_print(
      json_stack[json_type][json_num-1],
      json_stack[json_type][json_num])
  else:
    output_print(json.dumps(json_stack[json_type][json_num], indent=2))

def parse_log_file(filename, github_action=False):
  in_json = False
  in_json_type = None
  in_json_num = None
  pubber_json_num = 0
  json_lines = []
  global json_stack
  json_stack = {'config': {}, 'state': {}}

  f = open(filename, 'r')
  for l in f:
    l = l.strip('\n')
    if github_action:
      l = l[29:]
    debug_print('[in_json=%s (%s,%s)] %s', in_json, in_json_type, in_json_num, l)
    m_seq = RE_SEQUENCER_LINE.match(l) 
    m_pub = RE_PUBBER_LINE.match(l)

    if m_pub:
      assert(in_json==False)
      debug_print(m_pub.groupdict())
      m2 = RE_PUBBER_TAIL_JSON_BEGIN.match(m_pub['tail'])
      if m2:
        debug_print(m2.groupdict())
        in_json_type = m2.groupdict()['type'].lower()
        pubber_json_num += 1
        in_json_num = pubber_json_num
        in_json = True
      output_print(l)

    if m_seq:
      assert(in_json==False)
      debug_print(m_seq.groupdict())
      m2 = RE_SEQUENCER_TAIL_JSON_BEGIN.match(m_seq['tail']) 
      if m2:
        debug_print(m2.groupdict())
        in_json_type = m2.groupdict()['type']
        in_json_num = int(m2.groupdict()['num'])
        in_json = True
      output_print(l)

    if not m_pub and not m_seq and in_json: 
      json_lines.append(l)
      if l == '}':
        in_json = False 
   
    if not in_json and json_lines:
      debug_print(json_lines)
      json_obj = json.loads('\n'.join(json_lines))
      debug_print(json_obj)
      json_lines = []
      json_stack[in_json_type][in_json_num] = json_obj
      json_stack_print(in_json_type, in_json_num)
      in_json_type = None
      in_json_num = None

  debug_print(json_stack)

def main(args):
  options, args = getopt.getopt(args[1:], 'g')
  github_action = False
  for opt, optarg in options:
    if opt == '-g':
      github_action = True
  parse_log_file(args[0], github_action=github_action)
  return 0

if __name__ == '__main__':
  main(sys.argv)

