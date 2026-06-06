#!/usr/bin/env python3
"""
Example skin API server for AlexInd's Server Skins.

Place 64x64 PNG skins in the 'skins' directory, named as
<nickname>.png (e.g., 'Notch.png').

Usage:
    python server.py
"""

import json
import os
from http.server import HTTPServer, BaseHTTPRequestHandler

SKINS_DIR = os.path.join(os.path.dirname(__file__), "skins")
HOST = "0.0.0.0"
PORT = 8000


class SkinHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        nickname = self.path.strip("/")
        if not nickname:
            self.send_error(400, "Nickname is required")
            return

        skin_path = os.path.join(SKINS_DIR, nickname + ".png")

        if not os.path.isfile(skin_path):
            self.send_error(404, f"Skin not found for {nickname}")
            return

        skin_url = f"http://{self.client_address[0]}:{PORT}/skins/{nickname}.png"
        body = json.dumps({
            "SKIN": {
                "url": skin_url,
                "metadata": {}
            }
        }, indent=2)

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body.encode())


if __name__ == "__main__":
    os.makedirs(SKINS_DIR, exist_ok=True)
    server = HTTPServer((HOST, PORT), SkinHandler)
    print(f"Serving at http://{HOST}:{PORT}")
    print(f"Place skins in {SKINS_DIR}/<nickname>.png")
    server.serve_forever()
