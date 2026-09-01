import os
import tempfile
import unittest
from unittest.mock import patch, MagicMock
import subprocess
from engine.tools import ToolBelt


class TestToolBeltDirectory(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.TemporaryDirectory()
        self.workspace = self.test_dir.name
        os.makedirs(os.path.join(self.workspace, "subdir"))
        with open(os.path.join(self.workspace, "file1.txt"), "w") as f:
            f.write("hello")
        with open(os.path.join(self.workspace, "subdir", "file2.txt"), "w") as f:
            f.write("world")
        self.toolbelt = ToolBelt(workspace_root=self.workspace)

    def tearDown(self):
        self.test_dir.cleanup()

    def test_list_directory_cache_and_fallback(self):
        res = self.toolbelt.list_directory(".")
        self.assertIn("[DIR]  subdir/", res)
        self.assertIn("[FILE] file1.txt", res)

        res_sub = self.toolbelt.list_directory("subdir")
        self.assertIn("[FILE] file2.txt", res_sub)

    def test_list_directory_security_and_errors(self):
        res_sec = self.toolbelt.list_directory("../")
        self.assertIn("Permission denied", res_sec)

        res_notfound = self.toolbelt.list_directory("nonexistent")
        self.assertIn("not found", res_notfound)

        res_file = self.toolbelt.list_directory("file1.txt")
        self.assertIn("is a file", res_file)


class TestToolBeltReadFileLines(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.TemporaryDirectory()
        self.workspace = self.test_dir.name
        self.file_path = os.path.join(self.workspace, "test.txt")
        with open(self.file_path, "w") as f:
            f.write(chr(10).join([f"line {i}" for i in range(1, 10)]))
        self.toolbelt = ToolBelt(workspace_root=self.workspace)

    def tearDown(self):
        self.test_dir.cleanup()

    def test_read_single_file(self):
        res = self.toolbelt.read_file_lines(filepath="test.txt", start_line=2, end_line=4)
        self.assertIn("2: line 2", res)
        self.assertIn("3: line 3", res)
        self.assertIn("4: line 4", res)
        self.assertNotIn("1: line 1", res)
        self.assertNotIn("5: line 5", res)

    def test_read_batch_files(self):
        files_to_read = [
            {"filepath": "test.txt", "start_line": 1, "end_line": 2},
        ]
        res = self.toolbelt.read_file_lines(files_to_read=files_to_read)
        self.assertIn("--- File 1:", res)
        self.assertIn("1: line 1", res)

    def test_read_errors_and_validation(self):
        res_sec = self.toolbelt.read_file_lines(filepath="../outside.txt")
        self.assertIn("Permission denied", res_sec)

        res_bound = self.toolbelt.read_file_lines(filepath="test.txt", start_line=5, end_line=2)
        self.assertIn("end_line must be greater than", res_bound)

        res_nofile = self.toolbelt.read_file_lines()
        self.assertIn("No filepath or files_to_read", res_nofile)


class TestToolBeltGitOperations(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.TemporaryDirectory()
        self.workspace = self.test_dir.name
        self.toolbelt = ToolBelt(workspace_root=self.workspace)

    def tearDown(self):
        self.test_dir.cleanup()

    @patch("subprocess.check_output")
    def test_allowed_git_commands(self, mock_chk):
        mock_chk.return_value = "On branch main"
        res = self.toolbelt.git_read_operations(".", "status")
        self.assertEqual(res, "On branch main")
        mock_chk.assert_called_once()

    def test_rejected_and_injection_git_commands(self):
        res_rej = self.toolbelt.git_read_operations(".", "checkout")
        self.assertIn("Security Error: Git command ", res_rej)

        res_inj = self.toolbelt.git_read_operations(".", "status", args=["; rm -rf /"])
        self.assertIn("Security Error: Detected dangerous shell characters", res_inj)


class TestToolBeltGrepFileAndLogWindow(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.TemporaryDirectory()
        self.workspace = self.test_dir.name
        self.log_path = os.path.join(self.workspace, "app.log")
        with open(self.log_path, "w") as f:
            f.write("2026-06-26T11:00:00Z Event A" + chr(10))
            f.write("2026-06-26T11:00:10Z Event B" + chr(10))
            f.write("2026-06-26T11:01:00Z Event C" + chr(10))
        self.toolbelt = ToolBelt(workspace_root=self.workspace)

    def tearDown(self):
        self.test_dir.cleanup()

    @patch("subprocess.check_output")
    def test_grep_file(self, mock_chk):
        mock_chk.return_value = "2:2026-06-26T11:00:10Z Event B"
        res = self.toolbelt.grep_file("Event B", "app.log")
        self.assertIn("Event B", res)

    def test_expand_log_window(self):
        res = self.toolbelt.expand_log_window("app.log", "2026-06-26T11:00:05Z", window_seconds=10)
        self.assertIn("Event A", res)
        self.assertIn("Event B", res)
        self.assertNotIn("Event C", res)

    def test_expand_log_window_invalid_ts(self):
        res = self.toolbelt.expand_log_window("app.log", "invalid-ts")
        self.assertIn("Could not parse timestamp", res)


class TestToolBeltReadMethodDefinition(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.TemporaryDirectory()
        self.workspace = self.test_dir.name
        self.py_path = os.path.join(self.workspace, "sample.py")
        with open(self.py_path, "w") as f:
            f.write("def my_func(a, b):" + chr(10))
            f.write("    x = a + b" + chr(10))
            f.write("    return x" + chr(10))
            f.write("def other_func():" + chr(10))
            f.write("    pass" + chr(10))

        self.java_path = os.path.join(self.workspace, "Sample.java")
        with open(self.java_path, "w") as f:
            f.write("public class Sample {" + chr(10))
            f.write("    public void myMethod() {" + chr(10))
            f.write("        System.out.println(123);" + chr(10))
            f.write("    }" + chr(10))
            f.write("}" + chr(10))
        self.toolbelt = ToolBelt(workspace_root=self.workspace)

    def tearDown(self):
        self.test_dir.cleanup()

    def test_read_python_method(self):
        res = self.toolbelt.read_method_definition("sample.py", "my_func")
        self.assertIn("def my_func", res)
        self.assertIn("return x", res)
        self.assertNotIn("other_func", res)

    def test_read_java_method(self):
        res = self.toolbelt.read_method_definition("Sample.java", "myMethod")
        self.assertIn("public void myMethod()", res)
        self.assertIn("System.out.println", res)


class TestToolBeltSearchAndSymbols(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.TemporaryDirectory()
        self.workspace = self.test_dir.name
        self.code_file = os.path.join(self.workspace, "app.py")
        with open(self.code_file, "w") as f:
            f.write("class DeviceManager:\n    def start_device(self):\n        pass\n")
        self.toolbelt = ToolBelt(workspace_root=self.workspace)

    def tearDown(self):
        self.test_dir.cleanup()

    def test_grep_codebase(self):
        res = self.toolbelt.grep_codebase("DeviceManager")
        self.assertIn("DeviceManager", res)
        self.assertIn("app.py", res)

    def test_lookup_symbol(self):
        res = self.toolbelt.lookup_symbol("start_device")
        self.assertIn("start_device", res)


if __name__ == "__main__":
    unittest.main()
