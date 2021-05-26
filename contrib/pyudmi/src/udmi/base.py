import json
import os
import pytz
import datetime
import fastjsonschema
import udmi
import dateutil.parser

DEFAULT_UDMI_VERSION = 1

VALIDATORS = {}
PACKAGE_SCHEMATA_DIR = os.path.join(os.path.dirname(udmi.__file__), "schema")
UDMI_SCHEMATA_DIR = os.path.join(os.path.dirname(udmi.__file__), "..", "..", "..", "..", "schema")

SCHEMATA_DIR = PACKAGE_SCHEMATA_DIR if os.path.exists(PACKAGE_SCHEMATA_DIR) else UDMI_SCHEMATA_DIR


def get_path(uri):
    filename = os.path.basename(uri)
    path = "%s/%s" % (SCHEMATA_DIR, filename)
    with open(path, "r") as f:
        return json.loads(f.read())


def get_validator(name):
    validator = VALIDATORS.get(name)
    os.chdir(SCHEMATA_DIR)
    if validator is None:
        file_path = os.path.join(SCHEMATA_DIR, name)
        with open(file_path, "r") as f:
            schema = json.loads(f.read())
        handlers = {"file": get_path}
        validator = fastjsonschema.compile(schema, handlers=handlers)
        VALIDATORS[name] = validator

    return validator


class UDMIBase:
    schema = "none"

    def __init__(self, version):
        self.version = version
        self.validate()

    def __str__(self):
        return json.dumps(self.as_dict(), indent=4, sort_keys=True)

    def as_udmi(self):
        return json.dumps(self.as_dict(), indent=4, sort_keys=True)

    @classmethod
    def from_string(cls, s):
        return cls.from_dict(json.loads(s))

    @classmethod
    def from_dict(cls, d):
        return cls(**d)

    def as_dict(self):

        d = {}

        for name in self.__slots__:
            value = getattr(self, name, None)
            if value is not None:
                if hasattr(value, "as_dict"):
                    d[name] = value.as_dict()
                elif type(value) in (str, int, float, list, dict, tuple, bool):
                    d[name] = value
                else:
                    raise Exception("Can't serialise this value %s for json" % value)
        return d

    def validate(self):
        validator = get_validator(self.schema)
        validator(self.as_dict())

    @staticmethod
    def serialise_timestamp(timestamp):
        if isinstance(timestamp, str):
            dt = dateutil.parser.isoparse(timestamp)
        elif isinstance(timestamp, datetime.datetime):
            dt = timestamp
        else:
            raise Exception("Can't make sense of this timestamp %s" % timestamp)

        utc = pytz.utc
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=utc)
        else:
            dt = dt.astimezone(utc)
        as_iso = dt.isoformat("T") + "Z"
        fixed = as_iso.replace("+00:00", "")
        return fixed
