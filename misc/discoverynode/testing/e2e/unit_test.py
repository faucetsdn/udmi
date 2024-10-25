import test_local


def test_deep_merge():
    dict1 = {"a": {"b": 1, "c": {"d": 2}}}
    dict2 = {"a": {"c": 3, "c": {"d": 3}}}
    result = {"a": {"b": 1, "c": 3, "c": {"d": 3}}}
    assert test_local.deep_merge(dict1, dict2) == result

    metadata = {
        "pointset": {
            "points": {
                "filter_alarm_pressure_status": {
                    "units": "No-units",
                    "ref": "BV11.present_value",
                },
                "filter_differential_pressure_sensor": {
                    "units": "Degrees-Celsius",
                    "ref": "AV12.present_value",
                    "baseline_value": 10,
                    "baseline_tolerance": 2,
                },
                "filter_differential_pressure_setpoint": {
                    "units": "Bars",
                    "writable": True,
                    "baseline_value": 98,
                },
            }
        },
        "system": {
            "hardware": {"make": "BOS", "model": "pubber"},
            "software": {"firmware": "v1"},
            "location": {
                "site": "ZZ-TRI-FECTA-AHU",
                "section": "2-3N8C",
                "position": {"x": 111.0, "y": 102.3},
            },
            "physical_tag": {
                "asset": {"guid": "drw://TBC", "site": "ZZ-TRI-FECTA", "name": "AHU-1"}
            },
        },
        "cloud": {"auth_type": "RS256"},
        "discovery": {"families": {"vendor": {}}},
        "localnet": {
            "families": {
                "ipv4": {"addr": "192.168.2.1"},
                "ether": {"addr": "00:50:b6:ed:5f:77"},
                "vendor": {"addr": "28179023"},
            }
        },
        "testing": {
            "targets": {
                "applied": {
                    "target_point": "filter_differential_pressure_setpoint",
                    "target_value": 60,
                },
                "failure": {
                    "target_point": "filter_alarm_pressure_status",
                    "target_value": False,
                },
                "invalid": {
                    "target_point": "filter_differential_pressure_sensor",
                    "target_value": 15,
                },
            }
        },
        "version": "1.4.1",
        "timestamp": "2020-05-01T13:39:07Z",
    }

    expected_metadata = {
        "pointset": {
            "points": {
                "filter_alarm_pressure_status": {
                    "units": "No-units",
                    "ref": "BV11.present_value",
                },
                "filter_differential_pressure_sensor": {
                    "units": "Degrees-Celsius",
                    "ref": "AV12.present_value",
                    "baseline_value": 10,
                    "baseline_tolerance": 2,
                },
                "filter_differential_pressure_setpoint": {
                    "units": "Bars",
                    "writable": True,
                    "baseline_value": 98,
                },
            }
        },
        "system": {
            "hardware": {"make": "BOS", "model": "pubber"},
            "software": {"firmware": "v1"},
            "location": {
                "site": "ZZ-TRI-FECTA-AHU",
                "section": "2-3N8C",
                "position": {"x": 111.0, "y": 102.3},
            },
            "physical_tag": {
                "asset": {"guid": "drw://TBC", "site": "ZZ-TRI-FECTA", "name": "AHU-1"}
            },
        },
        "cloud": {"auth_type": "RS256"},
        "discovery": {"families": {"vendor": {}}},
        "localnet": {
            "families": {
                "ipv4": {"addr": "192.168.2.2"},
                "ether": {"addr": "00:50:b6:ed:5f:77"},
                "vendor": {"addr": "28179023"},
                "modbus": {"addr": "123"},
            }
        },
        "testing": {
            "targets": {
                "applied": {
                    "target_point": "filter_differential_pressure_setpoint",
                    "target_value": 60,
                },
                "failure": {
                    "target_point": "filter_alarm_pressure_status",
                    "target_value": False,
                },
                "invalid": {
                    "target_point": "filter_differential_pressure_sensor",
                    "target_value": 15,
                },
            }
        },
        "version": "1.4.1",
        "timestamp": "2020-05-01T13:39:07Z",
    }

    merged_metadata = test_local.deep_merge(
        metadata,
        {
            "localnet": {
                "families": {"ipv4": {"addr": "192.168.2.2"}, "modbus": {"addr": "123"}}
            }
        },
    )
    assert merged_metadata == expected_metadata
