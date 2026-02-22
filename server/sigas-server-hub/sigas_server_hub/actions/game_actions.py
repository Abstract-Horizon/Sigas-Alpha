from typing import Optional

from flask import Response, Request, g

from sigas_server_hub.flask_apps import external_route, permissions
from sigas_server_hub.game.game_manager import GameManager, Player
from sigas_server_hub.tokens import Token
from sigas_server_hub.users import UserManager, User
from sigas_server_hub.web_actions import WebActions


class GameActions(WebActions):
    def __init__(self, game_manager: GameManager, user_manager: UserManager) -> None:
        super().__init__()
        self.game_manager = game_manager
        self.user_manager = user_manager

    @external_route("/game", methods=["POST"], json_body="body")
    @permissions(["CREATE_GAME"])
    def create_game(self, request: Request, body: dict) -> Response:

        if "name" not in body:
            return self.error(400, "Expected 'name' value")

        token: Token = g.token

        user: Optional[User] = None
        if token.user_id is not None and token.user_id in self.user_manager.users:
            user = self.user_manager.users[token.user_id]

        alias = user.username if user is not None else None
        if alias is None and "alias" in body:
            alias = body["alias"]

        if alias is None:
            alias = f"anonymous_{self.user_manager.next_anonymous_number()}"

        master_player = Player(token.token, alias)

        game_name = body["name"]
        game = self.game_manager.create_game(game_name, master_player)

        return self.json_response({
            "name": game_name,
            "game_id": game.game_id,
            "url": self.game_manager.game_url(game),
            "master_player": {
                "token": token.token
            }
        })
