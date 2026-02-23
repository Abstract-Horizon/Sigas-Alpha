from typing import Optional

from sigas_alpha.player import Player


class Game:
    def __init__(self, game_id: str, game_name: str, url: str) -> None:
        self.game_id = game_id
        self.game_name = game_name
        self.players: dict[str, Player] = {}
        self.url = url
        self.master_player: Optional[Player] = None
