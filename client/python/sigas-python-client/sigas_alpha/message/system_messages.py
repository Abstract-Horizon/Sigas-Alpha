import struct
from typing import Optional

import time
from abc import ABC

from sigas_alpha.game.game import Game
from sigas_alpha.message.messages import JsonMessage, ZeroLenMessage, Message, register_message_type, FixedTypeMessageExtension
from sigas_alpha.player import Player


class HeloMessage(ZeroLenMessage):
    @staticmethod
    def create_typ() -> str: return "HELO"


class HeartBeatMessage(Message):
    @classmethod
    def from_body(cls, typ: str, client_id: str, flags: str, body: bytes) -> 'HeartBeatMessage':
        return HeartBeatMessage(struct.unpack(">H", body[:2])[0], client_id, flags)

    def __init__(self, sequence: int, client_id: str = "--", flags: str = "  "):
        super().__init__("HRTB", client_id, flags)
        self.sequence = sequence % 65536

    def body(self) -> bytes:
        return struct.pack(">H", self.sequence)


class JoinedMessage(JsonMessage):
    @staticmethod
    def create_typ() -> str: return "JOIN"


class LeftMessage(ZeroLenMessage):
    @staticmethod
    def create_typ() -> str: return "LEFT"


class ClientDisconnectedMessage(ZeroLenMessage):
    @staticmethod
    def create_typ() -> str: return "DISC"


class ClientReconnectedMessage(ZeroLenMessage):
    @staticmethod
    def create_typ() -> str: return "RECN"


class GameStartedMessage(JsonMessage):
    @staticmethod
    def create_typ() -> str: return "STRT"


class GameEndMessage(ZeroLenMessage):
    @staticmethod
    def create_typ() -> str: return "GEND"


class PrivateMsgMessage(JsonMessage):
    @staticmethod
    def create_typ() -> str: return "PMSG"


class PlayerListMessage(JsonMessage):
    @staticmethod
    def create_typ() -> str: return "PLST"

    def get_players(self) -> list[Player]:
        json_msg = self.json_body
        result = []
        if "players" in json_msg:
            for player_dict in json_msg["players"]:
                player_id = player_dict["player_id"] if "player_id" in player_dict else None
                alias = player_dict["alias"] if "alias" in player_dict else None
                if player_id is not None and alias is not None:
                    result.append(Player(player_id, alias))
        return result

    def apply_to_game(self, game: Game) -> None:
        players = self.get_players()
        for player in players:
            if player.player_id not in game.players:
                game.players[player.player_id] = player
            else:
                game.players[player.player_id].alias = player.alias


class PingPongMessage(Message, ABC):

    @classmethod
    def _current_time(cls) -> float:
        return float(int(time.time() * 1000) / 1000.0)

    @classmethod
    def from_body(cls, typ: str, client_id: str, flags: str, body: bytes) -> 'PingPongMessage':
        if typ == "PING":
            real_cls = PingMessage
        else:
            real_cls = PongMessage
        return real_cls(client_id, flags, struct.unpack(">Q", body)[0] / 1000.0)

    def __init__(self, typ: str, client_id: str = "--", flags: str = "  ", time: Optional[float] = None):
        super().__init__(typ, client_id, flags)
        self.time = time

    def body(self) -> bytes:
        return struct.pack(">Q", int(self.time * 1000))

    def __repr__(self) -> str:
        return f"{self.typ}[{self.flags}{self.client_id}]({self.time})"


class PingMessage(PingPongMessage):
    def __init__(self, client_id: str = "--", flags: str = "  ", time: float = PingPongMessage._current_time()):
        super().__init__("PING", client_id, flags, time)


class PongMessage(PingPongMessage):
    def __init__(self, client_id: str = "--", flags: str = "  ", time: float = PingPongMessage._current_time()):
        super().__init__("PONG", client_id, flags, time)


def _register_classes(*cls: FixedTypeMessageExtension) -> None:
    for c in cls:
        register_message_type(c.create_typ(), c)


_register_classes(
    HeloMessage,
    JoinedMessage,
    LeftMessage,
    ClientDisconnectedMessage,
    ClientReconnectedMessage,
    GameStartedMessage,
    GameEndMessage,
    PrivateMsgMessage,
    PlayerListMessage
)

register_message_type("HRTB", HeartBeatMessage)
register_message_type("PING", PingMessage)
register_message_type("PONG", PongMessage)
