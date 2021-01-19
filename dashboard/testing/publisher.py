#!/usr/bin/env python

# Copyright 2016 Google LLC. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""This application demonstrates how to perform basic operations on topics
with the Cloud Pub/Sub API.

For more information, see the README.md under /pubsub and the documentation
at https://cloud.google.com/pubsub/docs.
"""

import argparse
import yaml


def get_message(filename):
    return open(filename, 'r').read()


def get_attributes(attributes):
    with open(attributes) as stream:
        return yaml.safe_load(stream)


def publish_messages(project_id, topic_id, attributes, filename):
    """Publishes multiple messages to a Pub/Sub topic."""
    # [START pubsub_quickstart_publisher]
    # [START pubsub_publish]
    from google.cloud import pubsub_v1

    # TODO(developer)
    # project_id = "your-project-id"
    # topic_id = "your-topic-id"

    publisher = pubsub_v1.PublisherClient()
    # The `topic_path` method creates a fully qualified identifier
    # in the form `projects/{project_id}/topics/{topic_id}`
    topic_path = publisher.topic_path(project_id, topic_id)

    data = get_message(filename)
    data = data.encode("utf-8")
    attr = get_attributes(attributes)
    future = publisher.publish(topic_path, data, **attr)
    print(future.result())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("project_id", help="Your Google Cloud project ID")
    parser.add_argument("topic_id", help="Target topic for publish message")
    parser.add_argument("attributes", help="Filename of message attributes")
    parser.add_argument("filename", help="Filename of message contents")

    args = parser.parse_args()

    publish_messages(args.project_id, args.topic_id, args.attributes, args.filename)
