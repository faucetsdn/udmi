"""
Mirrors a pub/sub topic to another pub/sub topic by consuming a subscription
and republishing all messages
"""

import argparse
import base64
import json
import sys

from concurrent import futures
from functools import partial
from google import auth
from google.cloud import pubsub_v1

messages_processed = 0

def is_file_project(target: str) -> bool:
  return target == '//'

def subscribe_callback(message: pubsub_v1.subscriber.message.Message) -> None:
  global messages_processed
  publish(message)
  messages_processed += 1
  if messages_processed % 100 == 0:
    print(f'{messages_processed} messages processed')
  message.ack()

def topic_publisher(publisher, topic_path, message):
  publisher.publish(topic_path, message.data, **message.attributes)

def file_publisher(path: str, message):
  timestamp = message.publish_time.isoformat()
  file_path = f'{path}/{timestamp}.json'

  message_dict = {
    "data": str(base64.b64encode(message.data)),
    "timestamp": timestamp,
    "attributes": dict(message.attributes)
  }

  print('Writing ' + file_path)
  with open(file_path, "w") as outfile:
    outfile.write(json.dumps(message_dict, indent=2))

def parse_command_line_args():
  parser = argparse.ArgumentParser()
  parser.add_argument('source_project', type=str)
  parser.add_argument('source_subscription', type=str)
  parser.add_argument('target_project', type=str)
  parser.add_argument('target_topic', type=str)
  return parser.parse_args()

args = parse_command_line_args()

try:
  credentials, project_id = auth.default()
# pylint: disable-next=broad-except
except Exception as e:
  print(e)
  sys.exit()

subscriber = pubsub_v1.SubscriberClient(credentials=credentials)

if is_file_project(args.target_project):
  publish = partial(file_publisher, args.target_topic)
else:
  publisher = pubsub_v1.PublisherClient(credentials=credentials)
  topic_path = publisher.topic_path(args.target_project, args.target_topic)
  publish = partial(topic_publisher, publisher, topic_path)

subscription = subscriber.subscription_path(args.source_project, args.source_subscription)

future = subscriber.subscribe(subscription, subscribe_callback)
print('Listening to pubsub, please wait ...')

while True:
  try:
    future.result(timeout=5)
  except (futures.CancelledError, KeyboardInterrupt, futures.TimeoutError):
    print('Ending PubSub loop due to normal termination')
    future.cancel()
    future.result()
    break
  # pylint: disable-next=broad-except
  except Exception as ex:
    print(f'PubSub subscription failed with error: {ex}')
    future.cancel()
    future.result()
    break
