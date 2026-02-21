import base64
from typing import Optional

from flask import Flask, Response, Request

from sigas_server_hub.apps import external_route
from sigas_server_hub.sessions import SessionManager
from sigas_server_hub.web_actions import WebActions


class UserActions(WebActions):
    def __init__(self, session_manager: SessionManager) -> None:
        super().__init__()
        self.session_manager = session_manager

    @external_route("/login", methods=["POST"], headers=["Authorization"])
    def login(self, authorisation_header: Optional[str]) -> Response:
        if authorisation_header is None:
            return self.error(401, "Authorization header missing")

        if not authorisation_header.startswith("Basic "):
            return self.error(401, "Only Basic Authorization supported")

        base64_pair = authorisation_header[6:]
        try:
            pair = base64.b64decode(base64_pair).decode("ascii")
            i = pair.find(":")
            if i <= 0:
                return self.error(401, "Basic Authorization requires username:password format")
            username = pair[:i]
            password = pair[i + 1:]

        except:
            return self.error(401, "Problem with Basic Authorization")