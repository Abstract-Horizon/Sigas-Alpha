from abc import ABC, abstractmethod
from typing import Optional, Union, Any

import requests

from sigas_server_hub.utils import fast_random_hash, TOKEN_LENGTH, as_bool


OptionsType = dict[str, Union[str, int, bool, Any]]


class Player:
    def __init__(self, player_token: str, alias: str) -> None:
        self.player_id = "--"
        self.player_token = player_token
        self.alias = alias
        self.game: Optional[Game] = None
        self.master_player = False


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
    def __init__(self, game_manager: 'GameManager', game_name: str, master_player: Player) -> None:
        self.game_manager = game_manager
        self.game_id = "G_" + fast_random_hash(TOKEN_LENGTH)
        self.game_name = game_name
        self.server: Optional['Server'] = None
        self.master_player = master_player
        self.master_player.game = self
        self.master_player.player_id = "01"
        self.master_player.master_player = True
        self.players: dict[str, Player] = {}
        self.next_player_id = 2
        self.game_options = GameOptions()

    def close(self) -> None:
        self.server.remove_game(self)

    def add_player(self, player: Player) -> Player:
        for p in self.players.values():
            if p.player_token == player.player_token:
                return p

        player.player_id = f"{self.next_player_id:02x}"
        self.next_player_id += 1

        self.players[player.player_id] = player
        player.game = self
        player.master_player = False

        self.server.add_player(player)
        return player

    def start(self) -> 'Game':
        self.server.start_game(self)
        return self


class Server:
    def __init__(self, game_manager: 'GameManager', host: str, server_port: int, internal_port: int) -> None:
        self.game_manager = game_manager
        self.host = host
        self.server_port = server_port
        self.internal_port = internal_port
        self.games: dict[str, Game] = {}

    def _internal_url(self) -> str:
        return f"http://{self.host}:{self.internal_port}"

    def close(self) -> None:
        pass

    def add_game(self, game: Game) -> None:
        player = game.master_player
        body = {
            "master_token": player.player_token,
            "client_id": player.player_id,
            "alias": player.alias,
            "options": game.game_options.as_json()
        }
        requests.post(f"{self._internal_url()}/game/{game.game_id}", json=body)

    def remove_game(self, game: Game) -> None:
        # TODO - remove body
        requests.delete(f"{self._internal_url()}/game/{game.game_id}", json={})

    def start_game(self, game: Game) -> None:
        # TODO - remove body
        requests.put(f"{self._internal_url()}/game/{game.game_id}/start", json={})

    def add_player(self, player: Player) -> None:
        body = {
            "token": player.player_token,
            "client_id": player.player_id,
            "alias": player.alias
        }
        requests.post(f"{self._internal_url()}/game/{player.game.game_id}/client", json=body)

    def remove_player(self, player: Player) -> None:
        # TODO - remove body
        requests.delete(f"{self._internal_url()}/game/{player.game.game_id}/client", json={})


class GameManager(ABC):
    def __init__(self) -> None:
        self.games: dict[str, Game] = {}

    def create_game(self, game_name: str, master_player: Player) -> Game:
        game = Game(self, game_name, master_player)
        self.games[game.game_id] = game
        server = self.provision_server(game)

        game.server = server
        server.add_game(game)

        return game

    def remove_game(self, game: Game) -> None:
        if game.game_id in self.games:
            del self.games[game.game_id]

    @abstractmethod
    def game_url(self, game: Game) -> str:
        ...

    @abstractmethod
    def close_server(self) -> None:
        ...

    @abstractmethod
    def provision_server(self, game: Game) -> Server:
        ...
