#!/bin/bash

# Exit immediately if a command exits with a non-zero status, except for the test itself.
set -e

show_logs() {
  echo "### UDMIS LOG"
  cat "${WORK_DIR:-.}/out/udmis.log" || echo "No UDMIS log found"

  echo "### MOSQUITTO LOG"
  sudo cat /var/log/mosquitto/mosquitto.log || echo "No Mosquitto log found"

  echo "### REGISTRAR LOG"
  cat "${WORK_DIR:-.}/out/registrar.log" || echo "No Registrar log found"

  echo "### PUBBER LOG"
  cat "${WORK_DIR:-.}/out/pubber.log" || echo "No Pubber log found"

  echo "### ETCD DB"
  "${WORK_DIR:-.}/udmis/bin/etcdctl" get --prefix "" || echo "Failed to read etcd db"
}

failure_handler() {
  echo "!!! Error occurred in init_ci.sh !!!"
  show_logs
  echo "Starting interactive bash shell for debugging..."
  exec /bin/bash
}

trap failure_handler ERR

MOUNT_DIR="/home/runner/udmi_mount"
WORK_DIR="/home/runner/udmi"

echo "UDMI CI Replication Container Init"

TAR_FILE="/home/runner/udmi_src.tar"

if [ -f "$TAR_FILE" ]; then
  echo "Extracting source tarball $TAR_FILE to $WORK_DIR..."
  mkdir -p "$WORK_DIR"
  tar -xf "$TAR_FILE" -C "$WORK_DIR"
elif [ -d "$MOUNT_DIR" ] && [ -n "$(ls -A "$MOUNT_DIR" 2>/dev/null)" ]; then
  echo "Copying source from $MOUNT_DIR to $WORK_DIR for isolated runtime environment..."
  mkdir -p "$WORK_DIR"
  tar --exclude=.git --exclude=.gradle --exclude=build --exclude=out --exclude=.gemini --exclude=validator/build --exclude=udmis/build -cf - -C "$MOUNT_DIR" . | tar -xf - -C "$WORK_DIR"
else
  echo "Error: Neither source tarball ($TAR_FILE) nor mounted directory ($MOUNT_DIR) is available or valid."
  exit 1
fi

# Fix ownership of copied files and configure git safe directories
chown -R $(id -u):$(id -g) "$WORK_DIR"
git config --global --add safe.directory "*"

# Set up root directory symlinks so that UDMIS running inside docker can resolve its files
echo "Setting up root symlinks for UDMIS inside docker..."
sudo rm -rf /root/bin /root/udmi /root/var /root/build /root/etc
sudo ln -sf "$WORK_DIR"/udmis/bin /root/bin
mkdir -p "$WORK_DIR"/udmis/build/libs
sudo ln -sf "$WORK_DIR"/udmis/build /root/build
sudo ln -sf "$WORK_DIR" /root/udmi
sudo ln -sf "$WORK_DIR"/var /root/var
sudo ln -sf "$WORK_DIR"/udmis/etc /root/etc
sudo chmod 755 /root

cd "$WORK_DIR"

if [ $# -gt 0 ]; then
  echo "Executing requested command: $@"
  exec "$@"
fi

# Make sure we clean up any pre-existing local venv or out directories to prevent contamination
echo "Cleaning up any existing virtualenvs or outputs in the runtime directory..."
rm -rf venv out sites/udmi_site_model/out

echo "Running install_dependencies..."
bin/run_tests install_dependencies

  echo "Running test_registrar_dynamic..."
  set +e
  bin/test_registrar_dynamic
  TEST_RESULT=$?
  set -e

  if [ $TEST_RESULT -eq 0 ]; then
    echo "test_registrar_dynamic PASSED"
    exit 0
  else
    echo "test_registrar_dynamic FAILED with exit code: $TEST_RESULT"
    show_logs
#    echo "Starting interactive bash shell for debugging..."
#    exec /bin/bash
exit 1
  fi
