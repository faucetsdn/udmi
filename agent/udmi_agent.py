import argparse
import os

from udmi.schema import Config, State, BlobBlobsetState, BlobsetState, Common
from mqtt_manager import MqttManager
from git_manager import GitManager

def parse_command_line_args():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(
        description=("Example Google Cloud IoT Core MQTT device connection code.")
    )
    parser.add_argument(
        "--algorithm",
        choices=("RS256", "ES256"),
        required=True,
        help="Which encryption algorithm to use to generate the JWT.",
    )
    parser.add_argument(
        "--ca_certs",
        default="roots.pem",
        help="CA root from https://pki.google.com/roots.pem",
    )
    parser.add_argument(
        "--cloud_region", default="us-central1", help="GCP cloud region"
    )
    parser.add_argument(
        "--data",
        default="Hello there",
        help="The telemetry data sent on behalf of a device",
    )
    parser.add_argument("--device_id", required=True, help="Cloud IoT Core device id")
    parser.add_argument(
        "--jwt_expires_minutes",
        default=20,
        type=int,
        help="Expiration time, in minutes, for JWT tokens.",
    )
    parser.add_argument(
        "--listen_dur",
        default=60,
        type=int,
        help="Duration (seconds) to listen for configuration messages",
    )
    parser.add_argument(
        "--message_type",
        choices=("event", "state"),
        default="event",
        help=(
            "Indicates whether the message to be published is a "
            "telemetry event or a device state message."
        ),
    )
    parser.add_argument(
        "--mqtt_bridge_hostname",
        default="mqtt.googleapis.com",
        help="MQTT bridge hostname.",
    )
    parser.add_argument(
        "--mqtt_bridge_port",
        choices=(8883, 443),
        default=8883,
        type=int,
        help="MQTT bridge port.",
    )
    parser.add_argument(
        "--num_messages", type=int, default=100, help="Number of messages to publish."
    )
    parser.add_argument(
        "--private_key_file", required=True, help="Path to private key file."
    )
    parser.add_argument(
        "--project_id",
        default=os.environ.get("GOOGLE_CLOUD_PROJECT"),
        help="GCP cloud project name",
    )
    parser.add_argument(
        "--registry_id", required=True, help="Cloud IoT Core registry id"
    )
    parser.add_argument(
        "--service_account_json",
        default=os.environ.get("GOOGLE_APPLICATION_CREDENTIALS"),
        help="Path to service account json file.",
    )

    # Command subparser
    command = parser.add_subparsers(dest="command")

    return parser.parse_args()


class UdmiAgent:

    def __init__(self, args):
        self.mqtt_manager = MqttManager(args, self.on_message)
        self.git_manager = GitManager()
        self.device_state = State()
        self.device_state.blobset = BlobsetState()
        self.device_state.blobset.blobs = {}
        self.device_state.blobset.blobs['codebase'] = BlobBlobsetState()
        self.update_state(None, None, None)
        self.git_manager.restore()
        self.update_state("steady", self.git_manager.steady(None))

    def send_state(self):
        self.mqtt_manager.update_state(self.device_state.to_dict())

    def update_state(self, stage, result, status=None):
        blob = self.device_state.blobset.blobs['codebase']
        blob.stage = stage
        blob.result = result
        if status:
            blob.status = Common.Entry()
            blob.status.message = status
        else:
            blob.status = None
        self.send_state()

    def steady_state(self, target):
        self.update_state("steady", self.git_manager.steady(target))

    def fetch_target(self, target):
        self.update_state("fetch", self.git_manager.fetch(target))

    def apply_target(self, target):
        self.update_state("apply", self.git_manager.apply(target))

    def config_codebase(self, blob):
        stage = blob.stage
        target = blob.target
        self.update_state(stage, None, None)
        try:
            if stage == 'steady' or not stage:
                self.steady_state(target)
                print('steady state')
            elif stage == 'fetch':
                self.fetch_target(target)
            elif stage == 'apply':
                self.apply_target(target)
            else:
                raise Exception('unknown blob stage', stage)
        except Exception as e:
            print("Reporting exception", str(e))
            self.update_state(stage, None, str(e))

    def config_message(self, payload):
        config = Config.from_dict(payload)
        blobset = config.blobset
        if not blobset:
            print('ignoring config with no blobset')
            return
        if 'codebase' in blobset.blobs:
            self.config_codebase(blobset.blobs['codebase'])
        else:
            print('no codebase in blobset blobs')

    def on_message(self, topic, message):
        if topic == 'config':
            return self.config_message(message)
        print('Received unknown message topic {}'.format(topic))

    def loop(self):
        return self.mqtt_manager.loop()

def main():
    args = parse_command_line_args()

    agent = UdmiAgent(args)

    while agent.loop():
        pass

    print("Finished.")


if __name__ == "__main__":
    main()
