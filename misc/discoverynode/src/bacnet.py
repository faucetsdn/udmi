"""Sample for calling BACnet scan directly"""
import logging
import sys
import time
from unittest import mock
import udmi.discovery.bacnet
import datetime
import udmi.schema.config
import udmi.schema.state
import udmi.schema.util
import argparse


def main():
  stdout = logging.StreamHandler(sys.stdout)
  stdout.addFilter(lambda log: log.levelno < logging.WARNING)
  stdout.setLevel(logging.INFO)
  stderr = logging.StreamHandler(sys.stderr)
  stderr.setLevel(logging.WARNING)
  logging.root.setLevel(logging.INFO)

  parser = argparse.ArgumentParser(description="BACnet scan sample")
  parser.add_argument("ip", help="bacnet ip address")
  parser.add_argument("--depth", default="system", help="scan depth, one of: device,system,points")
  parser.add_argument("--addrs", default=None, help="comma seperated ip address targets" )
  args = parser.parse_args()

  state = udmi.schema.state.Discovery()
  stack = udmi.discovery.bacnet.create_bacnet_stack(ip=args.ip)
  a = udmi.discovery.bacnet.GlobalBacnetDiscovery(
      state, print, scan_id="families/bacnet", bacnet=stack
  )

  a.apply_config(
      udmi.schema.config.DiscoveryFamily(
          generation=udmi.schema.util.datetime_serializer(
              udmi.schema.util.current_time_utc()
              + datetime.timedelta(seconds=1)
          ),
          depth=args.depth,
          addrs=args.addrs.split(",") if args.addrs else None,
      )
  )
  while True:
    time.sleep(1)


if __name__ == "__main__":
  main()
