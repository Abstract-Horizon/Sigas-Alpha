import os
from logging import getLogger
from tempfile import TemporaryDirectory

import time

from sigas_server_hub.game.game_manager import GameManager, Game, Server, GameOptions
from sigas_server_hub.tokens import TokenManager
from sigas_server_hub.users import UserManager
from tests.sigas_alpha.client.server_setup import TestServerSetup

DAY_IN_SECONDS = 60*60*24


broker_server_port = 8081
broker_internal_port = 8082
hub_api_internal_port = 8083
hub_api_server_port = 8084


logger = getLogger(__name__)


class DummyGameManager(GameManager):
    def __init__(self) -> None:
        super().__init__()
        self.server = Server(self, "localhost", broker_server_port, broker_internal_port)
        self.next_token = "G_100"

    def _new_game_id(self) -> str:
        res = self.next_token
        self.next_token = f"G_{str(int(self.next_token[2:]) + 1)}"
        return res

    def provision_server(self, game: Game) -> Server:
        self.server.games[game.game_id] = game
        return self.server

    def close_server(self) -> None:
        pass

    def game_url(self, game: Game) -> str:
        return f"http://localhost:{broker_server_port}/game/stream/{game.game_id}"


class TestTokenManager(TokenManager):
    def __init__(self, token_file: str, expunge_trigger_ratio: float = 1) -> None:
        super().__init__(token_file, expunge_trigger_ratio=expunge_trigger_ratio)
        self.next_token = "100"

    def _new_token(self) -> str:
        res = self.next_token
        self.next_token = str(int(self.next_token) + 1)
        return res


with TemporaryDirectory() as temp_config_dir:

    token_file = os.path.join(temp_config_dir, "tokens.multijson")
    token_manager = TestTokenManager(token_file, expunge_trigger_ratio=1)

    users_file = os.path.join(temp_config_dir, "users.multijson")
    user_manager = UserManager(users_file, expunge_trigger_ratio=1)

    test_server_setup = TestServerSetup(
        game_manager_class=DummyGameManager,
        token_manager=token_manager,
        user_manager=user_manager,
        api_server_port=hub_api_server_port,
        api_internal_port=hub_api_internal_port
    )
    master_client = test_server_setup.clients["master"]

    logger.warning(f"Master hub token '{master_client.http_game_client.api_token}'")

    game = master_client.create_game("test_game", "master", GameOptions(heartbeat_period=1000), start_stream=False)

    player1_client = test_server_setup.add_player_client("player1", [])
    player2_client = test_server_setup.add_player_client("player2", [])
    player3_client = test_server_setup.add_player_client("player3", [])

    logger.warning(f"Player 1 hub token '{player1_client.http_game_client.api_token}'")
    logger.warning(f"Player 2 hub token '{player2_client.http_game_client.api_token}'")
    logger.warning(f"Player 3 hub token '{player3_client.http_game_client.api_token}'")

    logger.warning(f"Game id '{game.game_id}'")

    logger.warning("---------------------")

    logger.warning(f"curl -v -H \"Authorization: Token {master_client.http_game_client.stream_token}\" {master_client.http_game_client.stream_url}")

    logger.warning("Starting endless loop...")
    while True:
        time.sleep(1)
