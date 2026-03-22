import logging
import unittest
from typing import cast, Sequence

import time

from hamcrest import assert_that, contains_inanyorder

from sigas_alpha.player import Player
from tests.sigas_alpha.client.server_setup import TestServerSetup

logger = logging.getLogger()

logging.basicConfig(level=logging.DEBUG, format="%(asctime)s %(message)s")
logger.setLevel(logging.DEBUG)


def player_aliases(players: Sequence[Player]) -> list[str]:
    return [p.alias for p in players]


class TestJoinAndLeave(unittest.TestCase):

    def setUp(self) -> None:
        self.test_server_setup = TestServerSetup()
        self.master_client = self.test_server_setup.clients["master"]
        self.player1_client = self.test_server_setup.add_player_client("player1", [])
        self.player2_client = self.test_server_setup.add_player_client("player2", [])
        self.player3_client = self.test_server_setup.add_player_client("player3", [])

        time.sleep(0.1)

    def tearDown(self) -> None:
        self.test_server_setup.stop()

    def test_setup_game_and_sending_and_receiving_messages(self) -> None:
        game = self.test_server_setup.clients["master"].create_game("test_game", "main_alias")
        time.sleep(0.1)
        self.player1_client.join_name(game.game_id, "player1_alias")
        time.sleep(0.1)
        self.player2_client.join_name(game.game_id, "player2_alias")
        time.sleep(0.1)
        self.player3_client.join_name(game.game_id, "player3_alias")

        time.sleep(1)

        print("Waiting up to 10s...")
        started_time = time.time()
        while (time.time() - started_time < 10
               and (len(self.master_client.game.players) < 4
                    or len(self.player1_client.game.players) < 4
                    or len(self.player2_client.game.players) < 4
                    or len(self.player3_client.game.players) < 4)):
            time.sleep(0.1)

        assert_that(
            cast(Sequence, player_aliases(cast(Sequence, self.master_client.game.players.values()))),
            contains_inanyorder("main_alias", "player1_alias", "player2_alias", "player3_alias"),
            f"Got {self.master_client.game.players.values()}"
        )

        assert_that(
            cast(Sequence, player_aliases(cast(Sequence, self.player1_client.game.players.values()))),
            contains_inanyorder("main_alias", "player1_alias", "player2_alias", "player3_alias"),
            f"Got {self.player1_client.game.players.values()}"
        )

        assert_that(
            cast(Sequence, player_aliases(cast(Sequence, self.player2_client.game.players.values()))),
            contains_inanyorder("main_alias", "player1_alias", "player2_alias", "player3_alias"),
            f"Got {self.player2_client.game.players.values()}"
        )

        assert_that(
            cast(Sequence, player_aliases(cast(Sequence, self.player3_client.game.players.values()))),
            contains_inanyorder("main_alias", "player1_alias", "player2_alias", "player3_alias"),
            f"Got {self.player3_client.game.players.values()}"
        )

        print("Done")

    def test_leave_and_rejoin_messages(self) -> None:
        game = self.test_server_setup.clients["master"].create_game("test_game", "main_alias")
        time.sleep(0.1)
        logger.warning(f"----------------- adding player1 -----------")
        self.player1_client.join_name(game.game_id, "player1_alias")
        time.sleep(1)
        logger.warning(f"----------------- closing player1 -----------")
        self.player1_client.close()
        logger.warning(f"----------------- closed player1 -----------")
        time.sleep(2)
        logger.warning(f"----------------- adding player2 -----------")
        self.player2_client.join_name(game.game_id, "player2_alias")
        time.sleep(1)
        logger.warning(f"----------------- rejoining player1 -----------")
        self.player1_client.rejoin()

        time.sleep(1)

        print("Waiting up to 10s...")
        started_time = time.time()
        while (time.time() - started_time < 10
               and (len(self.master_client.game.players) < 3
                    or len(self.player1_client.game.players) < 3
                    or len(self.player2_client.game.players) < 3)):
            time.sleep(0.1)

        assert_that(
            cast(Sequence, player_aliases(cast(Sequence, self.master_client.game.players.values()))),
            contains_inanyorder("main_alias", "player1_alias", "player2_alias"),
            f"Got {self.master_client.game.players.values()}"
        )

        assert_that(
            cast(Sequence, player_aliases(cast(Sequence, self.player1_client.game.players.values()))),
            contains_inanyorder("main_alias", "player1_alias", "player2_alias"),
            f"Got {self.player1_client.game.players.values()}"
        )

        assert_that(
            cast(Sequence, player_aliases(cast(Sequence, self.player2_client.game.players.values()))),
            contains_inanyorder("main_alias", "player1_alias", "player2_alias"),
            f"Got {self.player2_client.game.players.values()}"
        )

        print("Done")
