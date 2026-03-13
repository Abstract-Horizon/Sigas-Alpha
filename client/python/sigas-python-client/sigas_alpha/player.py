

class Player:
    def __init__(self, player_id: str, alias: str) -> None:
        self.player_id = player_id
        self.alias = alias

    def __eq__(self, other) -> bool:
        if isinstance(other, Player):
            return self.player_id == other.player_id
        return False
