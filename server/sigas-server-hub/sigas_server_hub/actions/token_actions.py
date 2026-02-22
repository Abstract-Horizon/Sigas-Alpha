import base64
from typing import Optional

from flask import Flask, Response, Request

from sigas_server_hub.flask_apps import external_route, internal_route
from sigas_server_hub.sessions import SessionManager
from sigas_server_hub.tokens import TokenManager
from sigas_server_hub.web_actions import WebActions


class TokenActions(WebActions):
    def __init__(self, token_manager: TokenManager) -> None:
        super().__init__()
        self.token_manager = token_manager

    @internal_route("/token", methods=["POST"], json_body="body")
    def create_token(self, body: dict) -> Response:
        temporary = "temporary" in body and body["temporary"]

        if "lifespan" not in body:
            return self.error(400, "Expected 'lifespan' value")

        permissions = []
        if "permissions" in body:
            permissions_value = body["permissions"]
            if isinstance(permissions_value, list):
                permissions = permissions_value
            elif isinstance(permissions_value, str):
                permissions = body["permissions"].split("/")
            else:
                return self.error(400, f"Expected 'permissions' to be a string or array but got '{permissions_value}'")

        try:
            lifespan = float(body["lifespan"])
        except ValueError:
            return self.error(400, f"Need number for lifespan, but got '{body['lifespan']}'")

        note = body["note"] if "note" in body else ""

        token = self.token_manager.create_token(lifespan, permissions, note=note, temporary=temporary)

        return self.json_response(body=token.as_json())

    @internal_route("/token", methods=["GET"])
    def get_tokens(self) -> Response:
        return self.json_response(body=[token.as_json() for token in (self.token_manager.tokens.values())])

    def invalidate_token(self) -> Response:
        pass
