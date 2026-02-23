import random
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
    def create_game(self, body: dict) -> Response:
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
            "game_id": game.game_id,
            "game_name": game_name,
            "url": self.game_manager.game_url(game),
            "master_player": {
                "player_id": master_player.player_id,
                "alias": master_player.alias,
                "token": token.token
            }
        })

    @external_route("/game/<game_id>/join", methods=["POST"], json_body="body")
    @permissions()
    def join_game(self, game_id: str, body: dict) -> Response:
        token: Token = g.token

        user: Optional[User] = None
        if token.user_id is not None and token.user_id in self.user_manager.users:
            user = self.user_manager.users[token.user_id]

        alias = user.username if user is not None else None
        if alias is None and "alias" in body:
            alias = body["alias"]

        if alias is None:
            alias = f"anonymous_{self.user_manager.next_anonymous_number()}"

        player = Player(token.token, alias)

        if game_id not in self.game_manager.games:
            return self.error(404, f"Cannot find game with game id '{game_id}'")

        game = self.game_manager.games[game_id]

        for p in game.players.values():
            if player.player_token == p.player_token:
                player = p
            elif p.alias == player.alias:
                player.alias = f"{player.alias}{random.randint(1, 100)}"

        game.add_player(player)

        return self.json_response({
            "game_id": game.game_id,
            "game_name": game.game_name,
            "url": self.game_manager.game_url(game),
            "player": {
                "player_id": player.player_id,
                "alias": player.alias,
                "token": token.token
            }
        })

    @external_route("/game/<game_id>/start", methods=["POST"], json_body="body")
    @permissions(["CREATE_GAME"])
    def start_game(self, game_id: str, body: dict) -> Response:
        if game_id not in self.game_manager.games:
            return self.error(404, f"Cannot find game with game id '{game_id}'")

        game = self.game_manager.games[game_id]

        game.start()

        return self.json_response({
            "game_id": game.game_id,
            "game_name": game.game_name,
            "url": self.game_manager.game_url(game),
            "players": [
                { "player_id": player.player_id, "alias": player.alias, } for player in game.players.values()
            ]
        })
