from abc import ABC
from typing import TypeVar


class Message(ABC):
    def __init__(self) -> None:
        pass

    @classmethod
    def from_body(cls, typ: str, body: bytes) -> 'Message':
        return cls()

    def body(self) -> bytes:
        return bytes()

    @property
    def typ(self) -> str:
        return "UNKN"

    def __repr__(self) -> str:
        return f"{self.typ}({self.body()})"


class UnknownMessage(Message):
    def __init__(self, typ: str, body: bytes) -> None:
        super().__init__()
        self._typ = typ
        self._body = body

    @classmethod
    def from_body(cls, typ: str, body: bytes) -> 'Message':
        return UnknownMessage(typ, body)

    def body(self) -> bytes:
        return self._body

    @property
    def typ(self) -> str:
        return self._typ


class MessageWithClientId(Message, ABC):
    EMPTY_BODY = bytes([0, 0])

    def __init__(self, typ: str, client_id: str = "--") -> None:
        super().__init__()
        self._typ = typ
        self.client_id = client_id

    @classmethod
    def extract_client_id(cls, body: bytes) -> str:
        return body[:2].decode("ascii")

    @property
    def typ(self) -> str:
        return self._typ

    def body(self) -> bytes:
        return MessageWithClientId.EMPTY_BODY

    def __repr__(self) -> str:
        body = self.body()
        return f"{self.typ}({self.client_id}{', ' + str(body[2:]) if len(body) > 2 else ''})"


T_EXTENDS_MESSAGE = TypeVar("T_EXTENDS_MESSAGE", bound=Message)


MESSAGE_TYPES: dict[str, T_EXTENDS_MESSAGE] = {}


def register_message_type(typ: str, cls: T_EXTENDS_MESSAGE) -> None:
    MESSAGE_TYPES[typ] = cls


def create_message(typ: str, body: bytes) -> T_EXTENDS_MESSAGE:
    if typ in MESSAGE_TYPES:
        return MESSAGE_TYPES[typ].from_body(typ, body)

    raise NotImplemented(f"Message type {typ} not registered")
