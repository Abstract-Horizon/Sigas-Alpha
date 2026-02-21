from flask import Flask, Response, Request

from sigas_server_hub.apps import external_route, permissions
from sigas_server_hub.game.game_manager import GameManager
from sigas_server_hub.web_actions import WebActions


class GameActions(WebActions):
    def __init__(self, game_manager: GameManager) -> None:
        super().__init__()

    @external_route("/game", methods=["POST"], json_body="body")
    @permissions(["CREATE_GAME"])
    def create_game(self, request: Request, body: dict) -> Response:
        return Response(
            bytes(f"<html><body>Got request {body}</body></html>", "UTF-8"),
            status=200,
            mimetype="text/html")
