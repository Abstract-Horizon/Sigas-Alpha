from typing import Optional, Union, Any

from sigas_alpha.player import Player
from sigas_alpha.utils import as_bool

OptionsType = dict[str, Union[str, int, bool, Any]]


class GameOptions:
    @classmethod
    def extract_int(cls, name: str, dict: OptionsType, default: int) -> int:
        if name not in dict: return default
        res = int(dict[name])
        del dict[name]
        return res

    @classmethod
    def extract_bool(cls, name: str, dict: OptionsType, default: bool) -> bool:
        if name not in dict: return default
        res = as_bool(dict[name])
        del dict[name]
        return res

    def __init__(self, **kwargs) -> None:
        self.min_players: int = GameOptions.extract_int("min_players", kwargs, 2)
        self.max_players: int = GameOptions.extract_int("max_players", kwargs, 2)
        self.allow_late_join: bool = GameOptions.extract_bool("allow_late_join", kwargs, False)
        self.heartbeat_period: int = GameOptions.extract_int("heartbeat_period", kwargs, 2)

        self.other_options = kwargs

    def as_json(self) -> OptionsType:
        res = {
            "min_players": self.min_players,
            "max_players": self.max_players,
            "allow_late_join": self.allow_late_join,
            **self.other_options
        }
        return res


class Game:
    def __init__(self, game_id: str, game_name: str, url: str, game_options: GameOptions = GameOptions()) -> None:
        self.game_id = game_id
        self.game_name = game_name
        self.players: dict[str, Player] = {}
        self.url = url
        self.master_player: Optional[Player] = None
        self.game_options = game_options
