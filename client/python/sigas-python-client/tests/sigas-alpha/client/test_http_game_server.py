import unittest
from abc import ABC
from typing import cast, Sequence

import requests
import time
import struct
from threading import Thread

from hamcrest import assert_that, contains_exactly, is_, less_than

from sigas_alpha.client.http_game_client import HTTPGameClient
from sigas_alpha.game.message.Message import MessageWithClientId, register_message_type
from tests.utils.broker_setup import BrokerSetup


class HeloMessage(MessageWithClientId):
    @classmethod
    def from_body(cls, typ: str, body: bytes) -> 'HeloMessage': return HeloMessage(cls.extract_client_id(body))

    def __init__(self, client_id: str = "--"):
        super().__init__("HELO", client_id)


def _current_time() -> float:
    return float(int(time.time() * 1000) / 1000.0)


class PingPongMessage(MessageWithClientId, ABC):

    @classmethod
    def from_body(cls, typ: str, body: bytes) -> 'PingPongMessage':
        if typ == "PING":
            real_cls = PingMessage
        else:
            real_cls = PongMessage
        return real_cls(cls.extract_client_id(body), struct.unpack(">q", body[2:])[0] / 1000.0)

    def __init__(self, typ: str, client_id: str = "--", time: float = _current_time()):
        super().__init__(typ, client_id)
        self.time = time

    def body(self) -> bytes:
        return self.client_id.encode("ascii") + struct.pack(">q", int(self.time * 1000))

    def __repr__(self) -> str:
        return f"{self.typ}({self.client_id}, {self.time})"


class PingMessage(PingPongMessage):
    def __init__(self, client_id: str = "--", time: float = _current_time()):
        super().__init__("PING", client_id, time)


class PongMessage(PingPongMessage):
    def __init__(self, client_id: str = "--", time: float = _current_time()):
        super().__init__("PONG", client_id, time)


class TestHTTPGameServer(unittest.TestCase):

    def setUp(self) -> None:
        self.finished = False

        register_message_type("HELO", HeloMessage)
        register_message_type("PING", PingMessage)
        register_message_type("PONG", PongMessage)

        self.broker = BrokerSetup()
        self.broker.start()
        time.sleep(0.5)

        self.server_port = self.broker.server_port
        self.internal_port = self.broker.internal_port
        self.hub_port = self.broker.hub_port

        self._setup_game()

        self.master_http_client = HTTPGameClient(f"http://localhost:{self.server_port}/game", "333", "1234").start()
        self.client1_http_client = HTTPGameClient(f"http://localhost:{self.server_port}/game", "333", "1235").start()
        self.client2_http_client = HTTPGameClient(f"http://localhost:{self.server_port}/game", "333", "1236").start()

        self.master_messages = []
        self.client1_messages = []
        self.client2_messages = []

        self.master_thread = Thread(target=self._receive_messages, args=[self.master_http_client, "master", self.master_messages], daemon=True)
        self.client1_thread = Thread(target=self._receive_messages, args=[self.client1_http_client, "client1", self.client1_messages], daemon=True)
        self.clietn2_thread = Thread(target=self._receive_messages, args=[self.client2_http_client, "client2", self.client2_messages], daemon=True)

        self.master_thread.start()
        self.client1_thread.start()
        self.clietn2_thread.start()

        time.sleep(0.1)

    def tearDown(self) -> None:
        self.finished = True
        self.master_thread.join(1)
        self.client1_thread.join(1)
        self.clietn2_thread.join(1)
        self.broker.stop()

    def _setup_game(self) -> None:
        requests.post(f"http://localhost:{self.internal_port}/game/333", data='{"master_token": "1234", "client_id": "01"}')
        requests.post(f"http://localhost:{self.internal_port}/game/333/client", data='{"token": "1235", "client_id": "02"}')
        requests.post(f"http://localhost:{self.internal_port}/game/333/client", data='{"token": "1236", "client_id": "03"}')

        requests.put(f"http://localhost:{self.internal_port}/game/333/start", data='')

    def _receive_messages(self, client: HTTPGameClient, connection_type: str, messages_destination: list):
        while not self.finished:
            msg = client.get_message(True, 0.5)
            if msg is not None:
                client_id = "-"
                if isinstance(msg, MessageWithClientId):
                    client_id = msg.client_id
                print(f"{connection_type}: Client {client_id} received message {msg}")
                messages_destination.append(msg)
            else:
                time.sleep(0.1)

    def test_sending_and_receiving_messages(self) -> None:

        ping_message = PingMessage()
        pong_message = PongMessage("02")

        print("Sending messages as client 1235")
        self.client1_http_client.send_message(HeloMessage())
        self.client1_http_client.send_message(ping_message)
        print("Sent messages as client 1235")

        time.sleep(1)

        print("Sending messages as master 1234")
        self.master_http_client.send_message(pong_message)
        print("Sent messages as master 1234")

        print("Waiting up to 10s...")
        started_time = time.time()
        while time.time() - started_time < 10 and (len(self.master_messages) < 2 or len(self.client1_messages) < 1):
            time.sleep(0.1)

        lasted = time.time() - started_time
        assert_that(lasted, less_than(10.0), f"Timed out - wait lasted longer than 10s; {lasted}")

        ping_message.client_id = "02"
        expected_master_messages = [HeloMessage("02"), ping_message]
        expected_client1_messages = [pong_message]
        assert_that(cast(Sequence, self.master_messages), contains_exactly(*expected_master_messages), f"Got {self.master_messages}")
        assert_that(cast(Sequence, self.client1_messages), contains_exactly(*expected_client1_messages), f"Got {self.client1_messages}")

        print("Done")
