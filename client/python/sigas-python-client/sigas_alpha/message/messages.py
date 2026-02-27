import json
from abc import ABC
from typing import TypeVar, Any


class Message(ABC):
    EMPTY_BODY = bytes()

    def __init__(self, typ: str, client_id: str = "--", flags: str = "  ") -> None:
        self._typ = typ
        self.client_id = client_id
        self.flags = flags

    @classmethod
    def from_body(cls, typ: str, client_id: str, flags: str, body: bytes) -> 'Message':
        return cls()

    def body(self) -> bytes:
        return Message.EMPTY_BODY

    @property
    def typ(self) -> str:
        return self._typ

    def __repr__(self) -> str:
        body = self.body()
        return f"{self.typ}[{self.flags}{self.client_id}]{'(' + str(body[2:]) + ')' if len(body) > 0 else ''}"

    def __eq__(self, other) -> bool:
        if isinstance(other, Message):
            return self.typ == other.typ and self.client_id == other.client_id and self.flags == other.flags and self.body() == other.body()
        return False


class UnknownMessage(Message):
    def __init__(self, typ: str, client_id: str, flags: str, body: bytes) -> None:
        super().__init__(typ, client_id, flags)
        self._body = body

    @classmethod
    def from_body(cls, typ: str, client_id: str, flags: str, body: bytes) -> 'Message':
        return UnknownMessage(typ, client_id, flags, body)

    def body(self) -> bytes:
        return self._body

    @property
    def typ(self) -> str:
        return self._typ


class FixedTypeMessage(Message, ABC):
    @staticmethod
    def create_typ() -> str:
        ...


FixedTypeMessageExtension = TypeVar("FixedTypeMessageExtension", bound=FixedTypeMessage)


class ZeroLenMessage(FixedTypeMessage, ABC):
    @classmethod
    def from_body(cls, typ: str, client_id: str, flags: str, body: bytes) -> 'ZeroLenMessage': return cls(client_id, flags)

    def __init__(self, client_id: str = "--", flags: str = "  "):
        super().__init__(self.create_typ(), client_id, flags)


class JsonMessage(FixedTypeMessage, ABC):
    @classmethod
    def from_body(cls, typ: str, client_id: str, flags: str, body: bytes) -> 'JsonMessage':
        json_body = json.loads(body.decode("ascii"))
        return cls(json_body, client_id, flags)

    @staticmethod
    def create_typ() -> str:
        ...

    def __init__(self, json_body: dict[str, Any], client_id: str = "--", flags: str = "  "):
        super().__init__(self.create_typ(), client_id, flags)
        self.json_body = json_body


MessageExtension = TypeVar("MessageExtension", bound=Message)


MESSAGE_TYPES: dict[str, MessageExtension] = {}


def register_message_type(typ: str, cls: MessageExtension) -> None:
    MESSAGE_TYPES[typ] = cls


def create_message(typ: str, client_id: str, flags: str, body: bytes) -> MessageExtension:
    if typ in MESSAGE_TYPES:
        return MESSAGE_TYPES[typ].from_body(typ, client_id, flags, body)

    raise NotImplemented(f"Message type {typ} not registered")
