#!/bin/bash

## Fixed Config
######

# Name of run information (timestamp, UDMI version)
VERSION_FILE_NAME=version.json

# Directory to put summary (result.log and run information) into
GCS_SUMMARY_SUBDIR=summary

# Directory to put gzipped results into
GCS_RESULTS_SUBDIR=results

# Subscription to receieve notifications of completed run
NOTIFICATION_SUBSCRIPTION=sequencer_notification

# Subscription to PUBLISH notifications of completed run
NOTIFICATION_TOPIC=sequencer_notification

# directory to save results downloaded from GCS into (results deleted before each run)
LOCAL_RESULTS_DIR=results

# Name of device report (saved in local device folder)
DEVICE_REPORT_NAME=report.html

# Name of master report (saved in root of GCS bucket)
MASTER_REPORT_NAME=index.html

## From ENV
######

#GCS Bucket to put results in
#GCS_BUCKET=
