import os
import unittest
from abc import ABC
from tempfile import TemporaryDirectory
from typing import cast, Sequence

import requests
import time
import struct
from threading import Thread

from hamcrest import assert_that, contains_exactly, less_than

from sigas_alpha.client.http_game_client import HTTPGameClient
from sigas_alpha.game.message.Message import Message, register_message_type
from sigas_server_hub.game.test_game_manager import TestGameManager
from sigas_server_hub.sigas_hub import SigasHub


class HeloMessage(Message):
    @classmethod
    def from_body(cls, typ: str, client_id: str, flags: str, body: bytes) -> 'HeloMessage': return HeloMessage(client_id, flags)

    def __init__(self, client_id: str = "--", flags: str = "  "):
        super().__init__("HELO", client_id, flags)


def _current_time() -> float:
    return float(int(time.time() * 1000) / 1000.0)


class PingPongMessage(Message, ABC):

    @classmethod
    def from_body(cls, typ: str, client_id: str, flags: str, body: bytes) -> 'PingPongMessage':
        if typ == "PING":
            real_cls = PingMessage
        else:
            real_cls = PongMessage

        return real_cls(client_id, flags, struct.unpack(">q", body)[0] / 1000.0)

    def __init__(self, typ: str, client_id: str = "--", flags: str = "  ", time: float = _current_time()):
        super().__init__(typ, client_id, flags)
        self.time = time

    def body(self) -> bytes:
        return struct.pack(">q", int(self.time * 1000))

    def __repr__(self) -> str:
        return f"{self.typ}[{self.flags}{self.client_id}]({self.time})"


class PingMessage(PingPongMessage):
    def __init__(self, client_id: str = "--", flags: str = "  ", time: float = _current_time()):
        super().__init__("PING", client_id, flags, time)


class PongMessage(PingPongMessage):
    def __init__(self, client_id: str = "--", flags: str = "  ", time: float = _current_time()):
        super().__init__("PONG", client_id, flags, time)


class TestHTTPGameServer(unittest.TestCase):

    def setUp(self) -> None:
        self.finished = False

        register_message_type("HELO", HeloMessage)
        register_message_type("PING", PingMessage)
        register_message_type("PONG", PongMessage)

        # self.api_server_port = find_free_port()
        # self.api_internal_port = find_free_port()
        self.api_server_port = 9090
        self.api_internal_port = 9091

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

        self.master_token = self.api_server.token_manager.create_token(600, ["CREATE_GAME"], note="master", temporary=True)
        self.player1_token = self.api_server.token_manager.create_token(600, [], note="player1", temporary=True)
        self.player2_token = self.api_server.token_manager.create_token(600, [], note="player2", temporary=True)

        self.master_messages = []
        self.player1_messages = []
        self.player2_messages = []

        self.master_http_game_client = HTTPGameClient(f"http://localhost:{self.api_server_port}", self.master_token.token)
        self.player1_http_game_client = HTTPGameClient(f"http://localhost:{self.api_server_port}", self.player1_token.token)
        self.player2_http_game_client = HTTPGameClient(f"http://localhost:{self.api_server_port}", self.player2_token.token)

        time.sleep(0.1)

    def tearDown(self) -> None:
        self.finished = True
        self.master_http_game_client.stop_stream(0.2)
        self.player1_http_game_client.stop_stream(0.2)
        self.player2_http_game_client.stop_stream(0.2)
        self.api_server.stop()
        self.hub_thread.join(1)
        self.temp_config_dir.cleanup()

    def _receive_messages(self, client: HTTPGameClient, connection_type: str, messages_destination: list):
        while not self.finished:
            msg = client.get_message(True, 0.5)
            if msg is not None:
                messages_destination.append(msg)
            else:
                time.sleep(0.1)

    def _setup_game(self) -> None:
        requests.post(
            f"http://localhost:{self.api_server_port}/game",
            headers={"Authorization": f"Token {self.master_token}"},
            data='{"name": "test_game", "alias": "main_alias"}')
        requests.post(f"http://localhost:{self.api_server_port}/game/333/client", data='{"token": "1235", "client_id": "02"}')
        requests.post(f"http://localhost:{self.api_server_port}/game/333/client", data='{"token": "1236", "client_id": "03"}')

        requests.put(f"http://localhost:{self.api_internal_port}/game/333/start", data='')

    def test_setup_game_and_sending_and_receiving_messages(self) -> None:

        game, _ = self.master_http_game_client.create_game("test_game", "master_alias")
        self.player1_http_game_client.join_game(game.game_id, "player1_alias")
        self.player2_http_game_client.join_game(game.game_id, "player2_alias")

        self.master_http_game_client.start_game()

        self.master_http_game_client.start_stream()
        self.player1_http_game_client.start_stream()
        self.player2_http_game_client.start_stream()

        self.master_thread = Thread(target=self._receive_messages, args=[self.master_http_game_client, "master", self.master_messages], daemon=True)
        self.client1_thread = Thread(target=self._receive_messages, args=[self.player1_http_game_client, "player1", self.player1_messages], daemon=True)
        self.clietn2_thread = Thread(target=self._receive_messages, args=[self.player2_http_game_client, "player2", self.player2_messages], daemon=True)

        self.master_thread.start()
        self.client1_thread.start()
        self.clietn2_thread.start()
        try:

            ping_message = PingMessage()
            pong_message = PongMessage("02")

            print("Sending messages as client 1235")
            # time.sleep(0.2)
            self.player1_http_game_client.send_message(HeloMessage())
            # time.sleep(0.2)
            self.player2_http_game_client.send_message(ping_message)
            print("Sent messages as client 1235")

            time.sleep(1)

            print("Sending messages as master 1234")
            self.master_http_game_client.send_message(pong_message)
            print("Sent messages as master 1234")

            print("Waiting up to 10s...")
            started_time = time.time()
            while time.time() - started_time < 10 and (len(self.master_messages) < 2 or len(self.player1_messages) < 1):
                time.sleep(0.1)

            # lasted = time.time() - started_time
            # assert_that(lasted, less_than(10.0), f"Timed out - wait lasted longer than 10s; {lasted}")

            ping_message.client_id = "03"
            expected_master_messages = [HeloMessage("02"), ping_message]
            expected_client1_messages = [pong_message]
            assert_that(cast(Sequence, self.master_messages), contains_exactly(*expected_master_messages), f"Got {self.master_messages}")
            assert_that(cast(Sequence, self.player1_messages), contains_exactly(*expected_client1_messages), f"Got {self.player1_messages}")

        finally:
            self.master_thread.join(1)
            self.client1_thread.join(1)
            self.clietn2_thread.join(1)

        print("Done")
