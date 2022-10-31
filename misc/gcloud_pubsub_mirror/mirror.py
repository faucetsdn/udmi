import sys
import argparse

from concurrent import futures
from google import auth
from google.cloud import pubsub_v1

messages_processed = 0

def subscribe_callback(message: pubsub_v1.subscriber.message.Message) -> None:
    global messages_processed
    publisher.publish(topic_path, message.data, **message.attributes)
    messages_processed += 1
    if messages_processed % 100 == 0:
      print(f'{messages_processed} messages processed')
    message.ack()

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
except Exception as e:
    print(e)
    sys.exit()

sub_client = pubsub_v1.SubscriberClient(credentials=credentials)
publisher = pubsub_v1.PublisherClient(credentials=credentials)

topic_path = publisher.topic_path(args.target_project, args.target_topic)
future = sub_client.subscribe(f"projects/{args.source_project}/subscriptions/{args.source_subscription}", subscribe_callback)
print("Listening to pubsub, please wait ...")

while True:
    try:
        future.result(timeout=5)
    except futures.TimeoutError:
        continue
    except (futures.CancelledError, KeyboardInterrupt):
        future.cancel()
    except Exception as ex: 
        print(f"PubSub subscription failed with error: {ex}")
        future.cancel()
        break
