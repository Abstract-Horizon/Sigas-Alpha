import unittest
from typing import cast, Sequence

import requests
import time
from threading import Thread

from hamcrest import assert_that, contains_exactly, less_than

from sigas_alpha.client.http_game_client import HTTPGameClient
from sigas_alpha.message import PingMessage, PongMessage, HeloMessage
from tests.utils.broker_setup import BrokerSetup


class TestHTTPGameServer(unittest.TestCase):

    def setUp(self) -> None:
        self.finished = False

        self.broker = BrokerSetup()
        self.broker.start()
        time.sleep(0.5)

        self.server_port = self.broker.server_port
        self.internal_port = self.broker.internal_port
        self.hub_port = self.broker.hub_port

        self._setup_game()

        self.master_http_client = HTTPGameClient(f"http://localhost:{self.server_port}/game", "1234").start_stream()
        self.client1_http_client = HTTPGameClient(f"http://localhost:{self.server_port}/game", "1235").start_stream()
        self.client2_http_client = HTTPGameClient(f"http://localhost:{self.server_port}/game", "1236").start_stream()

        self.master_messages = []
        self.client1_messages = []
        self.client2_messages = []

        self.master_thread = Thread(target=self._receive_messages, args=[self.master_http_client, "master", self.master_messages], daemon=True)
        self.client1_thread = Thread(target=self._receive_messages, args=[self.client1_http_client, "client1", self.client1_messages], daemon=True)
        self.client2_thread = Thread(target=self._receive_messages, args=[self.client2_http_client, "client2", self.client2_messages], daemon=True)

        self.master_thread.start()
        self.client1_thread.start()
        self.client2_thread.start()

        time.sleep(0.1)

    def tearDown(self) -> None:
        self.finished = True
        self.master_thread.join(1)
        self.client1_thread.join(1)
        self.client2_thread.join(1)
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
