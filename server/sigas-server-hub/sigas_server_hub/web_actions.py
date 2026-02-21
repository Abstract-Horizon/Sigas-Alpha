import json
from abc import ABC
from typing import Union

from flask import Response


actions = {}


class WebActions(ABC):
    def __init__(self) -> None:
        actions[self.__class__] = self

    @staticmethod
    def error(status_code: int, msg: str) -> Response:
        return Response(
            bytes(f"<html><body>Error: {msg}</body></html>", "UTF-8"),
            status=status_code,
            mimetype="text/html")

    @staticmethod
    def json_response(body: Union[list, dict], status_code: int = 200) -> Response:
        return Response(
            response=json.dumps(body),
            status=status_code,
            mimetype="application/json")
