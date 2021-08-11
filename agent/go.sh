#!/bin/bash -e

export PYTHONPATH=$PWD/gencode/python

cd udmi_site_model

../venv/bin/python3 ../agent/udmi_agent.py \
                    --algorithm RS256 --private_key_file devices/AHU-1/rsa_private.pem --ca_certs ../roots.pem \
                    --device_id AHU-1 --registry_id ZZ-TRI-FECTA --project bos-daq-testing
