#!/usr/bin/env python3
"""Mock Akto ingestion endpoint for the shell-hook integration tests.

Test-only helper — the hooks themselves have no Python dependency. Reads the
verdict to serve from VERDICT (a JSON file) and appends every request body it
receives to CAPTURE so the tests can assert on the exact payload sent.
"""
import json
import os
import threading
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

VERDICT = os.environ.get("VERDICT", "")
CAPTURE = os.environ.get("CAPTURE", "/tmp/akto_capture.jsonl")
_lock = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        with _lock, open(CAPTURE, "a") as f:
            f.write(json.dumps({"url": self.path, "body": body.decode("utf-8", "replace")}) + "\n")

        verdict = {"Allowed": True}
        if VERDICT and os.path.exists(VERDICT):
            with open(VERDICT) as f:
                verdict = json.load(f)

        payload = json.dumps({"data": {"guardrailsResult": verdict}}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    # Threaded: the hooks are exercised concurrently, and a single-threaded
    # server serialises connections and deadlocks the parallel-writer test.
    ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Handler).serve_forever()
