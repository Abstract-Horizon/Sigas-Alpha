from flask import Response

from sigas_server_hub.apps import app_external, app_internal, external_route, internal_route
from sigas_server_hub.game.game_manager import GameManager
from sigas_server_hub.web_actions import WebActions


class StatusActions(WebActions):
    def __init__(self, game_manager: GameManager) -> None:
        super().__init__()
        self.game_manager = game_manager

    @external_route("/status", methods=["GET"])
    def internal_status(self) -> Response:
        return Response(
            bytes("<html><body>Status External: OK</body></html>", "utf8"),
            status=200,
            mimetype="text/html")

    @internal_route("/status", methods=["GET"])
    def internal_status(self) -> Response:
        return Response(
            bytes("<html><body>Status Internal: OK</body></html>", "utf8"),
            status=200,
            mimetype="text/html")
