from sigas_server_hub.game.game_manager import GameManager, Server, Game


class KubernetesGameManager(GameManager):
    def __init__(self) -> None:
        super().__init__()

    def provision_server(self, game: Game) -> Server:
        raise NotImplementedError

    def close_server(self) -> None:
        raise NotImplementedError

    def game_url(self, game: Game) -> str:
        raise NotImplementedError
