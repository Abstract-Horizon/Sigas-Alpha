import unittest
from typing import cast, Sequence

import time

from hamcrest import assert_that, contains_exactly, greater_than_or_equal_to

from sigas_alpha.game.game import GameOptions
from sigas_alpha.message import HeloMessage, JoinedMessage
from sigas_alpha.message.system_messages import PlayerListMessage

from tests.sigas_alpha.client.server_setup import TestServerSetup


class TestHeartBeat(unittest.TestCase):

    def setUp(self) -> None:
        self.test_server_setup = TestServerSetup()
        self.master_client = self.test_server_setup.clients["master"]
        self.player1_client = self.test_server_setup.add_player_client("player1", [])

        time.sleep(0.1)

    def tearDown(self) -> None:
        self.test_server_setup.stop()

    def test_setup_game_and_sending_and_receiving_messages(self) -> None:
        game = self.test_server_setup.clients["master"].create_game("test_game", "main_alias", game_options=GameOptions(
            heartbeat_period=0.5
        ))
        time.sleep(0.1)
        self.player1_client.join_name(game.game_id, "player1_alias")

        print("Waiting up to 10s...")
        started_time = time.time()
        while self.master_client.http_game_client.heartbeat_next_sequence < 6 and time.time() - started_time < 300:
            time.sleep(0.1)

        expected_master_messages = [
            JoinedMessage({"client_id": "02", "alias": "player1_alias"}, "02"), HeloMessage("02")
        ]
        assert_that(cast(Sequence, self.master_client.messages), contains_exactly(*expected_master_messages), f"Got {self.master_client.messages}")
        assert_that(cast(Sequence, self.player1_client.messages), contains_exactly(
            PlayerListMessage({"players": [{"player_id": "01", "alias": "main_alias"}, {"player_id": "02", "alias": "player1_alias"}]}, "02")
        ), f"Got {self.player1_client.messages}")
        assert_that(self.master_client.http_game_client.heartbeat_next_sequence, greater_than_or_equal_to(5), f"Got {self.master_client.http_game_client.heartbeat_next_sequence}")

        print("Done")
