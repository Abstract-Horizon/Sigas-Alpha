import os
from threading import Thread
from typing import Optional, Union, Callable

import time
from tempfile import TemporaryDirectory

from sigas_alpha.client.http_game_client import HTTPGameClient
from sigas_alpha.game.game import Game, GameOptions
from sigas_server_hub.game.game_manager import GameManager
from sigas_server_hub.game.test_game_manager import TestGameManager
from sigas_server_hub.sigas_hub import SigasHub
from sigas_server_hub.tokens import TokenManager, Token
from sigas_server_hub.users import UserManager
from sigas_server_hub.utils import Permissions
from tests.test_utils import find_free_port


class TestClient:
    def __init__(self,
                 name: str,
                 api_server: SigasHub,
                 permissions: Optional[Permissions] = None,
                 temporary_token: bool = True,
                 token_lifespan: int = 600,
                 token: Optional[Token] = None) -> None:
        self.name = name
        self.api_server = api_server
        self.token = token if token is not None else self.api_server.token_manager.create_token(token_lifespan, permissions, note=name, temporary=temporary_token)
        self.http_game_client = HTTPGameClient(f"http://localhost:{self.api_server.external_port}", self.token.token)
        self.messages = []
        self.finished = False
        self.game: Optional[Game] = None
        self.receive_thread: Optional[Thread] = None

    def _receive_messages(self, ):
        while not self.finished:
            msg = self.http_game_client.get_message(True, 0.5)
            if msg is not None:
                self.messages.append(msg)
            else:
                time.sleep(0.1)

    def stop(self) -> None:
        self.http_game_client.stop_stream(0.2)
        if self.receive_thread is not None:
            self.receive_thread.join(1)

    def create_game(self, game_name: str, alias: str, game_options: GameOptions = GameOptions(), start_stream: bool = True) -> Game:
        self.game, _ = self.http_game_client.create_game(game_name, alias, game_options)

        self.http_game_client.start_game()
        if start_stream:
            self.http_game_client.start_stream()
            self.receive_thread = Thread(target=self._receive_messages, args=[], daemon=True)
            self.receive_thread.start()
        return self.game

    def join_name(self, game_id: str, alias: str) -> None:
        self.game, _ = self.http_game_client.join_game(game_id, alias)
        self.http_game_client.start_stream()
        self.receive_thread = Thread(target=self._receive_messages, args=[], daemon=True)
        self.receive_thread.start()

    def close(self) -> None:
        self.http_game_client.stop_stream(2)
        self.receive_thread.join(0.1)

    def rejoin(self) -> None:
        self.http_game_client.reconnect_stream()
        self.receive_thread = Thread(target=self._receive_messages, args=[], daemon=True)
        self.receive_thread.start()


class TestServerSetup:
    def __init__(self,
                 game_manager_class: Union[type, Callable[[], GameManager]] = TestGameManager,
                 token_manager: Optional[TokenManager] = None,
                 user_manager: Optional[UserManager] = None,
                 api_server_port: int = find_free_port(),
                 api_internal_port: int = find_free_port()) -> None:
        self.api_server_port = api_server_port
        self.api_internal_port = api_internal_port

        if token_manager is None or user_manager is None:
            self.temp_config_dir = TemporaryDirectory()

            if token_manager is None:
                token_file = os.path.join(self.temp_config_dir.name, "tokens.multijson")
                token_manager = TokenManager(token_file, expunge_trigger_ratio=1)
            if user_manager is None:
                users_file = os.path.join(self.temp_config_dir.name, "users.multijson")
                user_manager = UserManager(users_file, expunge_trigger_ratio=1)

        self.token_manager = token_manager
        self.user_manager = user_manager

        self.api_server = SigasHub(
            self.api_server_port,
            self.api_internal_port,
            self.token_manager,
            self.user_manager,
            600,
            game_manager_class
        )

        self.hub_thread = Thread(target=self.api_server.start, daemon=True)
        self.hub_thread.start()
        time.sleep(0.1)

        self.clients: dict[str, TestClient] = {
            "master": TestClient(
                "master",
                self.api_server,
                ["CREATE_GAME"]
            )
        }

    def add_player_client(self, name: str, permissions: Optional[Permissions]) -> TestClient:
        client = TestClient(
            name,
            self.api_server,
            permissions
        )
        self.clients[name] = client
        return client

    def stop(self) -> None:
        for client in self.clients.values():
            client.stop()
        self.api_server.stop()
        self.hub_thread.join(1)
        self.temp_config_dir.cleanup()
