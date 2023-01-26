"""
Mirrors a pub/sub topic to another pub/sub topic by consuming a subscription
and republishing all messages
"""

import argparse
import datetime
import base64
import json
import os
import sys

from concurrent import futures
from functools import partial
from google import auth
from google.cloud import pubsub_v1

messages_processed = 0
messages = False
publish_futures = []

def is_file_project(target: str) -> bool:
  return target == '//'

def subscribe_callback(message: pubsub_v1.subscriber.message.Message) -> None:
  global messages_processed
  publish(message)
  messages_processed += 1
  if messages_processed % 100 == 0:
    print(f'{messages_processed} messages processed')
  message.ack()

def get_publish_callback(publish_future: pubsub_v1.publisher.futures.Future):
  global publish_futures
  def callback(publish_future: pubsub_v1.publisher.futures.Future) -> None:
    try:
      # Wait 60 seconds for the publish call to succeed.
      publish_future.result(timeout=60)
    except futures.TimeoutError:
      print(f"Publish timed out.")
  return callback

def topic_publisher(publisher, topic_path, message):
  print('Publishing', topic_path, message.publish_time)
  publish_future = publisher.publish(topic_path, message.data, **message.attributes)
  publish_future.add_done_callback(get_publish_callback(publish_future))
  publish_futures.append(publish_future)

def file_publisher(path: str, message):
  fullstamp = message.publish_time.isoformat() + 'Z'
  timestamp = fullstamp.replace('+00:00Z', 'Z').replace('000Z', 'Z')
  timepath = timestamp[0: timestamp.rindex(':')].replace(':', '/')
  file_path = f'{path}/{timepath}'
  os.makedirs(file_path, exist_ok = True)

  file_name = f'{file_path}/{timestamp}_{message.message_id}.json'

  message_dict = {
    "data": base64.b64encode(message.data).decode('utf-8'),
    "publish_time": timestamp,
    "attributes": dict(message.attributes)
  }

  print('Writing ' + file_name)
  with open(file_name, "w") as outfile:
    outfile.write(json.dumps(message_dict, indent=2))

def load_messages(path: str):
  print('Processing ' + path)
  if os.path.isfile(path):
    with open(path, 'r') as json_file:
      yield json.load(json_file)
  elif os.path.isdir(path):
    dirs = sorted(os.listdir(path))
    for subdir in dirs:
      yield from load_messages(path + '/' + subdir)
  else:
    raise Exception('Unknown file ' + path)

class Message:
  pass

def noop_ack():
  pass

def file_reader(messages, callback):
  global has_messages
  message = next(messages, None)
  if not message:
    has_messages = False
    return
  obj = Message()
  obj.data = base64.b64decode(message['data'])
  publish_time = message['publish_time'].replace('Z', '')
  obj.publish_time = datetime.datetime.fromisoformat(publish_time)
  obj.attributes = message['attributes']
  obj.ack = noop_ack
  callback(obj)

def parse_command_line_args():
  parser = argparse.ArgumentParser()
  parser.add_argument('source_project', type=str)
  parser.add_argument('source_subscription', type=str)
  parser.add_argument('target_project', type=str)
  parser.add_argument('target_topic', type=str)
  return parser.parse_args()

args = parse_command_line_args()

try:
  print('authenticating user')
  credentials, project_id = auth.default()
# pylint: disable-next=broad-except
except Exception as e:
  print(e)
  sys.exit()

if is_file_project(args.source_project):
  messages = load_messages(args.source_subscription)
  get_messages = partial(file_reader, messages, subscribe_callback)
  future = None
  has_messages = True
else:
  subscriber = pubsub_v1.SubscriberClient(credentials=credentials)
  subscription = subscriber.subscription_path(args.source_project, args.source_subscription)
  future = subscriber.subscribe(subscription, subscribe_callback)
  print('Listening to pubsub, please wait ...')
  get_messages = partial(future.result, timeout=10)
  has_messages = True

if is_file_project(args.target_project):
  publish = partial(file_publisher, args.target_topic)
else:
  publisher = pubsub_v1.PublisherClient(credentials=credentials)
  topic_path = publisher.topic_path(args.target_project, args.target_topic)
  publish = partial(topic_publisher, publisher, topic_path)


while has_messages:
  try:
    get_messages()
  except (futures.CancelledError, KeyboardInterrupt, futures.TimeoutError):
    print('Ending message loop due to normal termination')
    break
  # pylint: disable-next=broad-except
  except Exception as ex:
    print(f'Message loop failed with error: {ex}')
    break

if future:
  future.cancel()
  future.result()

print('Waiting for message publishing to complete...')
futures.wait(publish_futures, return_when=futures.ALL_COMPLETED)
