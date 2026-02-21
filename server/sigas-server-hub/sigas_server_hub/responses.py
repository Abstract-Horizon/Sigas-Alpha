import json
from typing import Union

from flask import Response


def error(status_code: int, msg: str) -> Response:
    return Response(
        bytes(f"<html><body>Error: {msg}</body></html>", "UTF-8"),
        status=status_code,
        mimetype="text/html")


def json_response(self, body: Union[list, dict], status_code: int = 200) -> Response:
    return Response(
        response=json.dumps(body),
        status=status_code,
        mimetype="application/json")
