import time
import BAC0
import os

from BAC0.core.devices.local.models import (
    analog_input,
    analog_output,
    analog_value,
    binary_input,
    binary_output,
    binary_value,
    character_string,
    date_value,
    datetime_value,
    humidity_input,
    humidity_value,
    make_state_text,
    multistate_input,
    multistate_output,
    multistate_value,
    temperature_input,
    temperature_value,
)
from BAC0.core.devices.local.object import ObjectFactory

bacnet = BAC0.lite(port=47808, deviceId=int(os.environ['BACNET_ID']))

ObjectFactory.clear_objects()

_new_objects = analog_input(
    name="Return Air Temperature",
    properties={"units": "degreesCelsius"},
    description="FCU-123",
    presentValue=20,
)

_new_objects = binary_input(
    name="Fan On",
    description="Main fan",
    presentValue=True,
)

_new_objects = analog_value(
    name="Room Temperature",
    properties={"units": "degreesCelsius"},
    description="Test Analogue Setpoint",
    is_commandable=True,
    presentValue=16,
)

_new_objects = multistate_value(
    description="L1 Fire Damper",
    properties={"stateText": make_state_text(["Open", "Closed"])},
    name="test_multistate_setpoint",
    is_commandable=True,
    presentValue=23,
)

_new_objects.add_objects_to_application(bacnet)

while True:
  bacnet.discover(global_broadcast=True)
  time.sleep(5)
