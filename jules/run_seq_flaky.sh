#!/bin/bash
export TARGET_PROJECT=//mqtt/localhost
export UDMI_REGISTRY_SUFFIX=
export UDMI_ALT_REGISTRY=

mkdir -p out/flaky_runs
for i in {1..10}; do
  echo "--- RUN $i ---"

  # Clean up existing local services for isolation per run
  kill $(pgrep -f "pubber") 2>/dev/null || true
  kill $(pgrep -f "udmis") 2>/dev/null || true
  kill $(pgrep -f "etcd") 2>/dev/null || true
  kill $(pgrep -f "mosquitto") 2>/dev/null || true
  kill $(pgrep -f "pull_messages") 2>/dev/null || true
  sleep 2

  bin/setup_base > /dev/null 2>&1
  bin/clone_model > /dev/null 2>&1
  bin/registrar sites/udmi_site_model > /dev/null 2>&1
  bin/start_local sites/udmi_site_model //mqtt/localhost > /dev/null 2>&1
  bin/pull_messages //mqtt/localhost > out/message_capture_${i}.log 2>&1 &
  sleep 5

  bin/test_etcd > /dev/null 2>&1
  bin/test_mosquitto > /dev/null 2>&1
  bin/test_regclean //mqtt/localhost > /dev/null 2>&1
  bin/test_runlocal > /dev/null 2>&1

  # Run the sequence without terminating if it "fails" the sanity check. Just grab out/sequencer.csv or RESULT log.
  bin/test_sequencer local full //mqtt/localhost $(< etc/local_tests.txt) > out/flaky_runs/run_${i}.log 2>&1 || true

  grep "RESULT" /app/sites/udmi_site_model/out/devices/AHU-1/RESULT.log > out/flaky_runs/results_${i}.txt || true

  echo "--- RUN $i FINISHED ---"
done
echo "All 10 runs complete" > out/flaky_runs/master.log
