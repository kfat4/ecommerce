#!/usr/bin/env python3
"""Serve the API console and forward its calls to the gateway (standard library only).

Why a proxy: the page and the gateway must share one origin, otherwise the browser blocks the calls
(CORS). The gateway deliberately has no CORS config, so instead of opening it up we serve the page
from the same origin and forward:

    /api/**, /actuator/**, /internal/**, /fallback/**  -> gateway   (GATEWAY_URL, default http://localhost:8080)
    /eureka/**                                          -> registry  (EUREKA_URL,  default http://localhost:8761)

/internal/** is forwarded on purpose: the gateway has no route for it, so you can see the 404 yourself.

Usage:  python3 tools/api-console/serve.py [port]        (default port 5500, listens on 127.0.0.1 only)
"""
import json
import os
import sys
import urllib.error
import urllib.request
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

GATEWAY = os.environ.get("GATEWAY_URL", "http://localhost:8080").rstrip("/")
EUREKA = os.environ.get("EUREKA_URL", "http://localhost:8761").rstrip("/")
GATEWAY_PREFIXES = ("/api/", "/actuator/", "/internal/", "/fallback/")
HOP_BY_HOP = {"connection", "keep-alive", "transfer-encoding", "upgrade", "proxy-connection", "te",
              "trailer", "content-length", "content-encoding", "host"}


class ConsoleHandler(SimpleHTTPRequestHandler):

    def _target(self):
        if self.path.startswith(GATEWAY_PREFIXES):
            return GATEWAY + self.path
        if self.path.startswith("/eureka/"):
            return EUREKA + self.path
        return None

    def _forward(self):
        target = self._target()
        if target is None:
            if self.command in ("GET", "HEAD"):
                return super().do_GET() if self.command == "GET" else super().do_HEAD()
            self.send_error(405)
            return
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else None
        headers = {k: v for k, v in self.headers.items() if k.lower() not in HOP_BY_HOP}
        headers["Accept-Encoding"] = "identity"
        request = urllib.request.Request(target, data=body, headers=headers, method=self.command)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                self._relay(response.status, response.headers, response.read())
        except urllib.error.HTTPError as error:          # 4xx/5xx still carry a useful body
            self._relay(error.code, error.headers, error.read())
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            payload = json.dumps({
                "title": "Upstream unreachable",
                "detail": f"{target} -> {getattr(error, 'reason', error)}. Is the stack running? (make up)",
            }).encode()
            self._relay(502, {"Content-Type": "application/problem+json"}, payload)

    def _relay(self, status, headers, payload):
        self.send_response(status)
        for key, value in headers.items():
            if key.lower() not in HOP_BY_HOP:
                self.send_header(key, value)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(payload)

    do_GET = do_HEAD = do_POST = do_PUT = do_DELETE = do_PATCH = _forward

    def log_message(self, fmt, *args):
        sys.stderr.write("%s  %s\n" % (self.log_date_time_string(), fmt % args))


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 5500
    handler = partial(ConsoleHandler, directory=os.path.dirname(os.path.abspath(__file__)))
    server = ThreadingHTTPServer(("127.0.0.1", port), handler)
    print(f"API console: http://localhost:{port}   (gateway {GATEWAY}, eureka {EUREKA})")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
