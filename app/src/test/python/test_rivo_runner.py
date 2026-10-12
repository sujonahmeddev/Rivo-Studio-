import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest


RUNNER_PATH = Path(__file__).resolve().parents[2] / "main" / "python" / "rivo_runner.py"
SPEC = importlib.util.spec_from_file_location("rivo_runner_under_test", RUNNER_PATH)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class RivoRunnerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.project = self.root / "project"
        self.project.mkdir()
        self.log = self.root / "run.log"
        self.cancel = self.root / "run.cancel"

    def tearDown(self):
        self.temp.cleanup()

    def run_script(self, source, filename="main.py"):
        script = self.project / filename
        script.parent.mkdir(parents=True, exist_ok=True)
        script.write_text(source, encoding="utf-8")
        return runner.run_file(str(self.project), filename, str(self.log), str(self.cancel))

    def test_run_file_captures_stdout_and_stderr(self):
        status = self.run_script("print('hello from Rivo')\nimport sys\nprint('warning', file=sys.stderr)\n")
        self.assertEqual(status, 0)
        output = self.log.read_text(encoding="utf-8")
        self.assertIn("hello from Rivo", output)
        self.assertIn("[stderr] warning", output)

    def test_system_exit_zero_is_a_successful_script_exit(self):
        self.assertEqual(self.run_script("raise SystemExit(0)\n"), 0)
        self.assertEqual(self.log.read_text(encoding="utf-8"), "")

    def test_system_exit_nonzero_preserves_exit_status(self):
        self.assertEqual(self.run_script("raise SystemExit(7)\n"), 7)
        self.assertIn("status 7", self.log.read_text(encoding="utf-8"))

    def test_user_stop_is_reported_as_exit_130(self):
        self.cancel.write_text("stop", encoding="utf-8")
        self.assertEqual(self.run_script("x = 1\n"), 130)
        self.assertIn("execution stopped by user", self.log.read_text(encoding="utf-8"))

    def test_runner_rejects_path_escape(self):
        with self.assertRaises(ValueError):
            runner.run_file(str(self.project), "../outside.py", str(self.log), str(self.cancel))

    def test_syntax_check_reports_a_real_line(self):
        result = runner.syntax_check("def broken(:\n    pass\n", "sample.py")
        self.assertIn("SyntaxError at line 1", result)

    def test_import_check_finds_local_module_and_reports_missing_import(self):
        (self.project / "local_helper.py").write_text("VALUE = 1\n", encoding="utf-8")
        result = json.loads(
            runner.check_imports(
                "import json\nimport local_helper\nimport rivo_definitely_missing_ci_module\n",
                str(self.project),
                "main.py",
            )
        )
        self.assertIn("json", result["imports"])
        self.assertIn("local_helper", result["imports"])
        self.assertEqual(result["missing"], ["rivo_definitely_missing_ci_module"])

    def test_requirement_report_does_not_claim_missing_packages_are_installed(self):
        (self.project / "requirements.txt").write_text(
            "json\nrivo-definitely-missing-ci-package>=1.0\n-e .\nrivo-direct-url @ https://example.invalid/pkg.whl\ngit+https://example.invalid/repo.git\n",
            encoding="utf-8",
        )
        result = json.loads(runner.check_requirements(str(self.project)))
        self.assertIn("json", result["bundled"])
        self.assertIn("rivo-definitely-missing-ci-package>=1.0", result["missing"])
        self.assertIn("Unsupported requirement syntax", result["message"])

    def test_log_redaction_masks_common_token_patterns(self):
        writer = runner._LogWriter(str(self.log))
        writer.write("api_key='sk_test_abcdefghijklmnop'\n123456:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghi\n")
        output = self.log.read_text(encoding="utf-8")
        self.assertNotIn("sk_test_abcdefghijklmnop", output)
        self.assertNotIn("123456:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghi", output)
        self.assertIn("[redacted]", output)
        self.assertIn("[Telegram token redacted]", output)


if __name__ == "__main__":
    unittest.main()
