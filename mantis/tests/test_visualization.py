"""Unit tests for Mantis Dual Visualization Engine (Graphviz DOT & Mermaid)."""

import os
import shutil
import tempfile
import unittest

from mantis.tools.registry import execute_tool
from mantis.tools.visualization import (
    generate_sequence_diagram,
    generate_topology_diagram,
    render_dot_to_svg,
)


class TestVisualizationEngine(unittest.TestCase):
    def setUp(self):
        self.udmi_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
        self.test_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_generate_topology_diagram_real_site_model(self):
        """Test topology diagram generation from official sites/udmi_site_model."""
        res = generate_topology_diagram(
            site_model="sites/udmi_site_model",
            format="both",
            udmi_root=self.udmi_root,
        )
        self.assertEqual(res["status"], "SUCCESS")
        self.assertGreater(res["device_count"], 0)
        self.assertIn("digraph SiteTopology", res["dot"])
        self.assertIn("graph LR", res["mermaid"])
        self.assertIn("```dot", res["rendered"])
        self.assertIn("```mermaid", res["rendered"])

    def test_generate_topology_diagram_with_focus(self):
        """Test topology generation with a focused device."""
        res = generate_topology_diagram(
            site_model="sites/udmi_site_model",
            focus_device="AHU-1",
            format="dot",
            udmi_root=self.udmi_root,
        )
        self.assertEqual(res["status"], "SUCCESS")
        self.assertIn('"AHU-1"', res["dot"])
        # Focused node gets highlight border #EA4335
        self.assertIn("#EA4335", res["dot"])
        self.assertIn("```dot", res["rendered"])
        self.assertNotIn("```mermaid", res["rendered"])

    def test_generate_topology_diagram_missing_model(self):
        """Test error handling when site model does not exist."""
        res = generate_topology_diagram(
            site_model="non_existent_site_model_xyz",
            udmi_root=self.udmi_root,
        )
        self.assertEqual(res["status"], "ERROR")
        self.assertIn("not found", res["error"])

    def test_generate_sequence_diagram(self):
        """Test sequence diagram generation from a test execution run directory."""
        # Create a mock run directory with sequence.log
        seq_log_content = (
            "2026-03-01T10:00:00Z INFO sequence: Starting test pointset_publish\n"
            "2026-03-01T10:00:01Z INFO sequence: Sent config RC:12345 to device AHU-1\n"
            "2026-03-01T10:00:02Z INFO sequence: Waiting for state update\n"
            "2026-03-01T10:00:03Z INFO sequence: Cutoff time set to 2026-03-01T10:00:01Z\n"
            "2026-03-01T10:00:04Z INFO sequence: Received state update for AHU-1\n"
            "2026-03-01T10:00:05Z INFO sequence: RESULT pass pointset pointset_publish STABLE 1/8 Sequence complete\n"
        )
        with open(os.path.join(self.test_dir, "sequence.log"), "w") as f:
            f.write(seq_log_content)

        res = generate_sequence_diagram(
            run_dir=self.test_dir,
            title="Custom Sequence Test",
            format="both",
            udmi_root=self.udmi_root,
        )
        self.assertEqual(res["status"], "SUCCESS")
        self.assertGreater(res["event_count"], 0)
        self.assertIn("sequenceDiagram", res["mermaid"])
        self.assertIn("digraph SequenceFlow", res["dot"])
        self.assertIn("Custom Sequence Test", res["mermaid"])
        self.assertIn("Custom Sequence Test", res["dot"])

    def test_render_dot_to_svg(self):
        """Test compiling valid Graphviz DOT to SVG."""
        dot_code = """
        digraph G {
            rankdir=LR;
            A -> B [label="test"];
        }
        """
        res = render_dot_to_svg(dot_code)
        self.assertEqual(res["status"], "SUCCESS")
        self.assertIn("<svg", res["svg"])
        self.assertIn("</svg>", res["svg"])

    def test_render_dot_to_svg_invalid(self):
        """Test compiling invalid Graphviz DOT returns error."""
        res = render_dot_to_svg("invalid syntax { not a dot graph")
        self.assertEqual(res["status"], "ERROR")
        self.assertIn("Graphviz dot compilation failed", res["error"])

    def test_render_dot_to_svg_missing_binary(self):
        """Test that missing dot binary returns explicit missing executable error."""
        from unittest.mock import patch
        with patch("os.path.isfile", return_value=False), patch("shutil.which", return_value=None):
            res = render_dot_to_svg("digraph G { A -> B; }")
            self.assertEqual(res["status"], "ERROR")
            self.assertIn("not found on system PATH", res["error"])


    def test_visualization_tools_in_registry(self):
        """Test dispatching visualization tools via execute_tool."""
        # Test topology
        res_topo = execute_tool(
            "generate_topology_diagram",
            {"site_model": "sites/udmi_site_model", "format": "mermaid"},
            udmi_root=self.udmi_root,
        )
        self.assertEqual(res_topo["status"], "SUCCESS")
        self.assertIn("graph LR", res_topo["mermaid"])

        # Test render_dot_to_svg
        res_dot = execute_tool(
            "render_dot_to_svg",
            {"dot_content": "digraph D { X -> Y; }"},
            udmi_root=self.udmi_root,
        )
        self.assertEqual(res_dot["status"], "SUCCESS")
        self.assertIn("<svg", res_dot["svg"])


if __name__ == "__main__":
    unittest.main()
