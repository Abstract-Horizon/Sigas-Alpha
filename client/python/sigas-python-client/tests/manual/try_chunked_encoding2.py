import sys
from abc import ABC

import requests
import time
import struct
from threading import Thread

from sigas_alpha.client.http_game_client import HTTPGameClient
from sigas_alpha.game.message.Message import Message, register_message_type


class HeloMessage(Message):
    @classmethod
    def from_body(cls, typ: str, client_id: str, flags: str, body: bytes) -> 'HeloMessage': return HeloMessage(client_id, flags)

    def __init__(self, client_id: str = "--", flags: str = "  "):
        super().__init__("HELO", client_id, flags)


class PingPongMessage(Message, ABC):
    @classmethod
    def from_body(cls, typ: str, client_id: str, flags: str, body: bytes) -> 'PingPongMessage':
        if typ == "PING":
            real_cls = PingMessage
        else:
            real_cls = PongMessage
        return real_cls(client_id, flags, struct.unpack(">q", body)[0] / 1000.0)

    def __init__(self, typ: str, client_id: str = "--", flags: str = "  ", time: float = time.time()):
        super().__init__(typ, client_id, flags)
        self.time = time

    def body(self) -> bytes:
        return struct.pack(">q", int(self.time * 1000))

    def __repr__(self) -> str:
        return f"{self.typ}[{self.flags}{self.client_id}]({self.time})"


class PingMessage(PingPongMessage):
    def __init__(self, client_id: str = "--", flags: str = "  ", time: float = time.time()):
        super().__init__("PING", client_id, flags, time)


class PongMessage(PingPongMessage):
    def __init__(self, client_id: str = "--", flags: str = "  ", time: float = time.time()):
        super().__init__("PONG", client_id, flags, time)


register_message_type("HELO", HeloMessage)
register_message_type("PING", PingMessage)
register_message_type("PONG", PongMessage)


requests.post('http://localhost:8082/game/333', data='{"master_token": "1234", "client_id": "01"}')
requests.post('http://localhost:8082/game/333/client', data='{"token": "1235", "client_id": "02"}')
requests.post('http://localhost:8082/game/333/client', data='{"token": "1236", "client_id": "03"}')

requests.put('http://localhost:8082/game/333/start', data='')

master_http_client = HTTPGameClient("http://localhost:8081", "1234").start_stream("333", "1234")
client1_http_client = HTTPGameClient("http://localhost:8081", "1235").start_stream("333", "1235")
client2_http_client = HTTPGameClient("http://localhost:8081", "1236").start_stream("333", "1236")


master_messages = []
client1_messages = []
client2_messages = []

finished = False


def receive_messages(client: HTTPGameClient, connection_type: str, messages_destination: list):
    while not finished:
        msg = client.get_message(True, 0.5)
        if msg is not None:
            client_id = "-"
            if isinstance(msg, MessageWithClientId):
                client_id = msg.client_id
            print(f"{connection_type}: Client {client_id} received message {msg}")
            messages_destination.append(msg)
        else:
            time.sleep(0.1)


master_thread = Thread(target=receive_messages, args=[master_http_client, "master", master_messages], daemon=True)
client1_thread = Thread(target=receive_messages, args=[client1_http_client, "client1", client1_messages], daemon=True)
clietn2_thread = Thread(target=receive_messages, args=[client2_http_client, "client2", client2_messages], daemon=True)

master_thread.start()
client1_thread.start()
clietn2_thread.start()
try:
    time.sleep(0.2)


    def message_generator(messages: list[bytes]):
        for msg in messages:
            yield msg


    print("Sending messages as client 1235")
    client1_http_client.send_message(HeloMessage())
    client1_http_client.send_message(PingMessage())
    print("Sent messages as client 1235")

    time.sleep(1)

    print("Sending messages as master 1234")
    master_http_client.send_message(PongMessage("02"))
    print("Sent messages as master 1234")

    print("Waiting up to 10s...")
    now = time.time()
    while time.time() - now < 10 and len(master_messages) < 2 and len(client1_messages) < 1:
        time.sleep(0.1)


finally:
    finished = True
    master_thread.join(1)
    client1_thread.join(1)
    clietn2_thread.join(1)


print("Done")

# sys.exit(0)
