import unittest
from typing import cast, Sequence

import time

from hamcrest import assert_that, contains_exactly

from sigas_alpha.message import PingMessage, PongMessage, HeloMessage

from tests.sigas_alpha.client.server_setup import TestServerSetup


class TestHTTPGameServer(unittest.TestCase):

    def setUp(self) -> None:
        self.finished = False

        self.test_server_setup = TestServerSetup()
        self.master_client = self.test_server_setup.clients["master"]
        self.player1_client = self.test_server_setup.add_player_client("player1", [])
        self.player2_client = self.test_server_setup.add_player_client("player2", [])

        time.sleep(0.1)

    def tearDown(self) -> None:
        self.finished = True
        self.test_server_setup.stop()

    def test_setup_game_and_sending_and_receiving_messages(self) -> None:
        game = self.test_server_setup.clients["master"].create_game("test_game", "main_alias")
        time.sleep(0.1)
        self.player1_client.join_name(game.game_id, "player1_alias")
        time.sleep(0.1)
        self.player2_client.join_name(game.game_id, "player2_alias")

        ping_message = PingMessage()
        pong_message = PongMessage("02")

        time.sleep(0.2)
        self.player2_client.http_game_client.send_message(ping_message)

        time.sleep(1)

        print("Sending messages as master 1234")
        self.master_client.http_game_client.send_message(pong_message)
        print("Sent messages as master 1234")

        print("Waiting up to 10s...")
        started_time = time.time()
        while time.time() - started_time < 10 and (len(self.master_client.messages) < 3 or len(self.player1_client.messages) < 1):
            time.sleep(0.1)

        # lasted = time.time() - started_time
        # assert_that(lasted, less_than(10.0), f"Timed out - wait lasted longer than 10s; {lasted}")

        ping_message.client_id = "03"
        expected_master_messages = [HeloMessage("02"), HeloMessage("03"), ping_message]
        expected_client1_messages = [pong_message]
        assert_that(cast(Sequence, self.master_client.messages), contains_exactly(*expected_master_messages), f"Got {self.master_client.messages}")
        assert_that(cast(Sequence, self.player1_client.messages), contains_exactly(*expected_client1_messages), f"Got {self.player1_client.messages}")

        print("Done")
