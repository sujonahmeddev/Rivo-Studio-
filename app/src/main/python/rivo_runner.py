"""Small, app-bundled bridge for the Chaquopy local Python runtime.

This module is not a shell. It executes one user-selected Python script, with the
project folder as its working directory, inside the Android app process.
"""

import ast
import importlib.metadata
import importlib.util
import json
import os
import re
import runpy
import sys
import traceback

MAX_LOG_BYTES = 1024 * 1024
_TELEGRAM_TOKEN = re.compile(r"\b\d{6,}:[A-Za-z0-9_-]{20,}\b")
_SECRET_ASSIGNMENT = re.compile(
    r"(?i)(api[_-]?key|access[_-]?token|bot[_-]?token|password|secret)(\s*[:=]\s*['\"]?)[A-Za-z0-9._-]{8,}"
)


def _safe_log_text(value):
    value = _TELEGRAM_TOKEN.sub("[Telegram token redacted]", value)
    return _SECRET_ASSIGNMENT.sub(r"\1\2[redacted]", value)


class _LogWriter:
    def __init__(self, log_path, label=""):
        self.log_path = log_path
        self.label = label
        self.truncation_written = False

    def write(self, value):
        if not value:
            return 0
        safe = _safe_log_text(str(value))
        if self.label:
            safe = self.label + safe
        try:
            size = os.path.getsize(self.log_path) if os.path.exists(self.log_path) else 0
            if size >= MAX_LOG_BYTES:
                if not self.truncation_written:
                    with open(self.log_path, "a", encoding="utf-8", errors="replace") as stream:
                        stream.write("\n[output truncated after 1 MiB]\n")
                    self.truncation_written = True
                return len(value)
            remaining = MAX_LOG_BYTES - size
            with open(self.log_path, "a", encoding="utf-8", errors="replace") as stream:
                stream.write(safe[:remaining])
                stream.flush()
        except OSError:
            # A full device should not cause the runtime bridge to hide the script's
            # own exception. The Kotlin side reports the resulting empty/partial log.
            pass
        return len(value)

    def flush(self):
        return None

    def isatty(self):
        return False

    @property
    def encoding(self):
        return "utf-8"


def _inside(root, candidate):
    root = os.path.realpath(root)
    candidate = os.path.realpath(candidate)
    return candidate == root or candidate.startswith(root + os.sep)


def run_file(project_root, relative_path, log_path, cancel_path):
    root = os.path.realpath(project_root)
    script_path = os.path.realpath(os.path.join(root, *relative_path.split("/")))
    if not _inside(root, script_path):
        raise ValueError("Script path is outside the authorized project.")
    if not os.path.isfile(script_path):
        raise FileNotFoundError("The selected Python file is missing.")

    old_cwd = os.getcwd()
    old_path = list(sys.path)
    old_argv = list(sys.argv)
    old_stdout, old_stderr = sys.stdout, sys.stderr
    os.makedirs(os.path.dirname(log_path), exist_ok=True)
    with open(log_path, "w", encoding="utf-8", errors="replace"):
        pass
    stdout = _LogWriter(log_path)
    stderr = _LogWriter(log_path, "[stderr] ")

    def cancellation_trace(frame, event, arg):
        if event == "line" and os.path.exists(cancel_path):
            raise KeyboardInterrupt("Execution stopped by the user.")
        return cancellation_trace

    try:
        os.chdir(root)
        sys.path.insert(0, os.path.dirname(script_path))
        sys.path.insert(0, root)
        sys.argv = [script_path]
        sys.stdout, sys.stderr = stdout, stderr
        sys.settrace(cancellation_trace)
        runpy.run_path(script_path, run_name="__main__")
        return 0
    except KeyboardInterrupt:
        if os.path.exists(cancel_path):
            stdout.write("\n[execution stopped by user]\n")
            return 130
        traceback.print_exc(file=stderr)
        return 130
    except SystemExit as error:
        code = error.code
        if code is None:
            return 0
        if isinstance(code, int):
            if code != 0:
                stderr.write("\n[script exited with status {}]\n".format(code))
            return code
        stderr.write(str(code) + "\n")
        return 1
    except BaseException:
        traceback.print_exc(file=stderr)
        return 1
    finally:
        sys.settrace(None)
        sys.stdout, sys.stderr = old_stdout, old_stderr
        sys.argv = old_argv
        sys.path[:] = old_path
        os.chdir(old_cwd)


def syntax_check(source, filename):
    try:
        compile(source, filename, "exec")
        return ""
    except SyntaxError as error:
        location = "" if error.lineno is None else " at line {}".format(error.lineno)
        return "SyntaxError{}: {}".format(location, error.msg)
    except Exception as error:
        return "Syntax check failed: {}".format(type(error).__name__)


def _top_level_imports(source):
    tree = ast.parse(source)
    result = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            result.update(alias.name.split(".", 1)[0] for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.level == 0 and node.module:
            result.add(node.module.split(".", 1)[0])
    return sorted(result)


def check_imports(source, project_root, script_path):
    try:
        names = _top_level_imports(source)
    except SyntaxError as error:
        return json.dumps({"ok": False, "error": "SyntaxError at line {}: {}".format(error.lineno, error.msg), "missing": []})
    old_path = list(sys.path)
    try:
        root = os.path.realpath(project_root)
        script_directory = os.path.realpath(os.path.join(root, os.path.dirname(script_path)))
        if not _inside(root, script_directory):
            return json.dumps({"ok": False, "error": "Project path validation failed.", "missing": []})
        sys.path.insert(0, script_directory)
        sys.path.insert(0, root)
        missing = []
        for name in names:
            try:
                if importlib.util.find_spec(name) is None:
                    missing.append(name)
            except (ImportError, ValueError, AttributeError):
                missing.append(name)
        return json.dumps({"ok": not missing, "imports": names, "missing": missing})
    finally:
        sys.path[:] = old_path


def check_requirements(project_root):
    path = os.path.join(project_root, "requirements.txt")
    if not os.path.isfile(path):
        return json.dumps({"requirements": [], "bundled": [], "missing": [], "message": "No requirements.txt was found."})

    requirements = []
    unsupported_lines = []
    try:
        with open(path, "r", encoding="utf-8-sig", errors="replace") as stream:
            for raw_line in stream:
                line = raw_line.split("#", 1)[0].strip()
                if not line:
                    continue
                if (
                    line.startswith(("-", ".", "/"))
                    or re.match(r"(?i)(?:git|hg|svn|bzr)\+", line)
                    or re.match(r"(?i)https?://", line)
                    or re.search(r"\s@\s*", line)
                ):
                    unsupported_lines.append(line)
                    continue
                match = re.match(r"\s*([A-Za-z0-9_.-]+)", line)
                if match:
                    requirements.append((line, match.group(1)))
                else:
                    unsupported_lines.append(line)
    except OSError as error:
        return json.dumps({"requirements": [], "bundled": [], "missing": [], "message": "Could not read requirements.txt: {}".format(error)})

    bundled, missing = [], []
    module_aliases = {
        "python-telegram-bot": "telegram",
        "pytelegrambotapi": "telebot",
        "pillow": "PIL",
        "pyyaml": "yaml",
    }
    for original, package_name in requirements:
        normalized = re.sub(r"[-_.]+", "-", package_name).lower()
        module_name = module_aliases.get(normalized, package_name.replace("-", "_"))
        found = False
        try:
            importlib.metadata.distribution(package_name)
            found = True
        except importlib.metadata.PackageNotFoundError:
            try:
                found = importlib.util.find_spec(module_name) is not None
            except (ImportError, ValueError, AttributeError):
                found = False
        (bundled if found else missing).append(original)

    message = (
        "All listed packages appear available in the embedded runtime."
        if not missing and not unsupported_lines
        else "Chaquopy packages Python libraries into the APK at build time; Rivo does not run device-time pip installs. "
             "Missing or unsupported requirements are reported, not silently installed."
    )
    if unsupported_lines:
        message += " Unsupported requirement syntax: " + ", ".join(unsupported_lines[:8])
    return json.dumps({
        "requirements": [item[0] for item in requirements],
        "bundled": bundled,
        "missing": missing,
        "message": message,
    })
