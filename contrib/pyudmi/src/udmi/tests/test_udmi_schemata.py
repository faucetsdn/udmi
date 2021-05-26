import unittest
import datetime
import os
from fastjsonschema import JsonSchemaException

from udmi.base import SCHEMATA_DIR
from udmi.config import Config
from udmi.discover import Discover
from udmi.metadata import MetaData
from udmi.event_pointset import Pointset, EventPointset
from udmi.event_system import EventSystem
from udmi.envelope import Envelope
from udmi.properties import Properties
from udmi.state import State

os.chdir(SCHEMATA_DIR)

class TestCreateTopLevelObjects(unittest.TestCase):

    def test_create_config(self):
        timestamp = datetime.datetime.utcnow()
        system = {
            "min_loglevel": 500
        }
        gateway = {
            "proxy_ids": ["AGH-725", "HAT-6"]
        }
        pointset = {
            "sample_limit_sec": 2,
            "sample_rate_sec": 500,
            "points": {
                "return_air_temperature_sensor": {
                    "set_value": 21.1
                }
            }
        }

        config1 = Config(timestamp, system, pointset, gateway)
        # check without the optional bits
        _ = Config(timestamp, system)
        # try round trip
        udmi = str(config1)
        remade = Config.from_string(udmi)
        self.assertEqual(config1.as_dict(), remade.as_dict())

    def test_create_discover(self):
        timestamp = datetime.datetime.utcnow()
        protocol = "bacnet"
        local_id = "92EA09"
        points = {
            "reading_value": {
                "units": "C",
                "present_value": 21.30108642578125
            }
        }

        discover = Discover(timestamp, protocol, local_id, points)

        # try round trip
        udmi = str(discover)
        remade = Discover.from_string(udmi)
        self.assertEqual(discover.as_dict(), remade.as_dict())


    def test_create_metadata(self):
        timestamp = datetime.datetime.utcnow()
        system = {
            "location": {
                "site": "US-SFO-XYY",
                "section": "NW-2F",
                "position": {
                    "x": 10,
                    "y": 20
                }
            },
            "physical_tag": {
                "asset": {
                    "guid": "bim://04aEp5ymD_$u5IxhJN2aGi",
                    "name": "USSFO-234567"
                }
            }
        }
        hash = "12345678"
        gateway = {
            "gateway_id": "GAT-12"
        }

        pointset = {
            "points": {
                "return_air_temperature_sensor": {
                    "units": "Degrees-Celsius"
                }
            }
        }

        meta_data = MetaData(timestamp, system, hash=hash, gateway=gateway, pointset=pointset)

        # try round trip
        udmi = str(meta_data)
        remade = MetaData.from_string(udmi)
        self.assertEqual(meta_data.as_dict(), remade.as_dict())
        _meta_data = MetaData(timestamp, system)


    def test_create_pointset(self):
        timestamp = datetime.datetime.utcnow()
        points = {
            "reading_value": {
                "present_value": 21.30108642578125
            },
            "yoyo_motion_sensor": {
                "present_value": True
            },
            "enum_value": {
                "present_value": "hello"
            }
        }

        pointset = EventPointset(timestamp, points)
        # try round trip
        udmi = str(pointset)
        remade = Pointset.from_string(udmi)
        self.assertEqual(pointset.as_dict(), remade.as_dict())
        self.assertRaises(JsonSchemaException, Pointset, timestamp, None)


    def test_create_system_event(self):
        timestamp = datetime.datetime.utcnow()
        logentries = [{
            "message": "hello",
            "category": "one.two.three",
            "timestamp": timestamp,
            "level": 200
        }]

        event_system = EventSystem(timestamp, logentries)
        # try round trip
        udmi = str(event_system)
        remade = EventSystem.from_string(udmi)
        self.assertEqual(event_system.as_dict(), remade.as_dict())


    def test_create_envelope(self):
        envelope = Envelope("projectprojectid", "device_reg_id", "45", "GHB-001", "pointset")
        # try round trip
        udmi = str(envelope)
        remade = Envelope.from_string(udmi)
        self.assertEqual(envelope.as_dict(), remade.as_dict())

    def test_create_properties(self):

        key_type = "RSA_PEM"
        connect = "direct"

        properties = Properties(key_type, connect)

        # try round trip
        udmi = str(properties)
        remade = Properties.from_string(udmi)
        self.assertEqual(properties.as_dict(), remade.as_dict())

    def test_create_state(self):
        timestamp = datetime.datetime.utcnow()
        system = {
            "make_model": "ACME Bird Trap",
            "firmware": {
                "version": "3.2a"
            },
            "operational": True,
            "serial_no": "1234567890"
        }

        state = State(timestamp, system, pointset=None)

        # try round trip
        udmi = str(state)
        remade = State.from_string(udmi)
        self.assertEqual(state.as_dict(), remade.as_dict())
