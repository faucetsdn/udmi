import time
import BAC0
import os

bacnet = BAC0.lite(port=47808, deviceId=int(os.environ['BACNET_ID']))

while True:
  bacnet.discover(global_broadcast=True)
  time.sleep(5)