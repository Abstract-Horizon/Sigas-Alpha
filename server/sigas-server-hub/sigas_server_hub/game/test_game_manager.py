from sigas_server_hub.test.broker_setup import BrokerSetup
from sigas_server_hub.game.game_manager import GameManager, Server, Game


class TestGameManager(GameManager):
    def __init__(self) -> None:
        super().__init__()
        self.broker_setup = BrokerSetup()
        self.broker_setup.start()
        self.server = Server(self, "localhost", self.broker_setup.server_port, self.broker_setup.internal_port)

    def provision_server(self, game: Game) -> Server:
        self.server.games[game.game_id] = game
        return self.server

    def close_server(self) -> None:
        pass

    def game_url(self, game: Game) -> str:
        return f"http://localhost:{self.broker_setup.server_port}/game/{game.game_id}"
