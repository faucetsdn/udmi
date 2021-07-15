import time
import subprocess

SAVE_FILE = '/tmp/udmi_agent.applied'

class GitManager:
    def __init__(self):
        self._nonce = None
        self._applied = None

    def restore(self):
        try:
            current = self._exec('git rev-list -n 1 HEAD')
            print('Current version', current)
            print('Loading comitted version from %s' % SAVE_FILE)
            with open(SAVE_FILE, 'r') as fd:
                saved = fd.readlines()[0].strip()
            if current != saved:
                print('Comitted != saved, restoring', saved)
                self.apply(saved)
            else:
                print('Comitted == saved', saved)
        except Exception as e:
            raise Exception('Error loading comitted version: %s' % str(e))

    def _exec(self, cmd):
        print('executing: %s' % cmd)
        cmd_args = cmd.split(' ')
        process = subprocess.run(cmd_args, capture_output=True, check=False)
        if process.returncode:
            print('execution failed: %s, %s, %s' % (
                process.returncode, process.stdout, process.stderr))
            message = process.stderr.decode('utf-8').strip()
            raise Exception('Failed subshell execution: %s' % message)
        return process.stdout.decode('utf-8').strip()

    def steady(self, target):
        if self._applied and target == self._nonce:
            print('Target/nonce match, writing', self._applied, SAVE_FILE)
            with open(SAVE_FILE, 'w') as fd:
                fd.write(self._applied)
        elif target:
            print('Target/nonce mismatch', target, self._nonce)
        result = self._exec('git describe')
        print('HEAD description', result)
        return result

    def fetch(self, target):
        self._exec('git fetch origin %s' % target)
        result = self._exec('git rev-list -n 1 %s' % target)
        print('Target rev', result)
        return result

    def apply(self, target):
        self._nonce = None
        self._applied = target
        previous = self._exec('git rev-list -n 1 HEAD')
        print(self._exec('git reset --hard %s' % target))
        current = self._exec('git rev-list -n 1 HEAD')
        if current != target:
            print(self._exec('git reset --hard %s' % previous))
            raise Exception('Target HEAD mismatch')
        self._nonce = str(time.time())
        print('Apply nonce', self._nonce)
        return self._nonce
