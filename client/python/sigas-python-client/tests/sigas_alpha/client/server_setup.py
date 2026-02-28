import os
from threading import Thread
from typing import Optional

import time
from tempfile import TemporaryDirectory

from sigas_alpha.client.http_game_client import HTTPGameClient
from sigas_alpha.game.game import Game
from sigas_server_hub.game.test_game_manager import TestGameManager
from sigas_server_hub.sigas_hub import SigasHub
from sigas_server_hub.utils import Permissions
from tests.test_utils import find_free_port


class TestClient:
    def __init__(self,
                 name: str,
                 api_server: SigasHub,
                 permissions: Optional[Permissions],
                 temporary_token: bool = True,
                 token_lifespan: int = 600) -> None:
        self.name = name
        self.api_server = api_server
        self.token = self.api_server.token_manager.create_token(token_lifespan, permissions, note=name, temporary=temporary_token)
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

    def create_game(self, game_name: str, alias: str) -> Game:
        self.game, _ = self.http_game_client.create_game(game_name, alias)

        self.http_game_client.start_game()
        self.http_game_client.start_stream()
        self.receive_thread = Thread(target=self._receive_messages, args=[], daemon=True)
        self.receive_thread.start()
        return self.game

    def join_name(self, game_id: str, alias: str) -> None:
        self.http_game_client.join_game(game_id, alias)
        self.http_game_client.start_stream()
        self.receive_thread = Thread(target=self._receive_messages, args=[], daemon=True)
        self.receive_thread.start()


class TestServerSetup:
    def __init__(self) -> None:
        self.api_server_port = find_free_port()
        self.api_internal_port = find_free_port()
        # self.api_server_port = 9090
        # self.api_internal_port = 9091

        self.temp_config_dir = TemporaryDirectory()

        self.token_file = os.path.join(self.temp_config_dir.name, "tokens.multijson")
        self.users_file = os.path.join(self.temp_config_dir.name, "users.multijson")

        self.api_server = SigasHub(
            self.api_server_port,
            self.api_internal_port,
            self.token_file,
            self.users_file,
            1,
            600,
            TestGameManager
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
