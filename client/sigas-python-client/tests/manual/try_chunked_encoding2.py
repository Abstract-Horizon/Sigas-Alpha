import sys
from abc import ABC

import requests
import time
import struct
from threading import Thread

from sigas_alpha.client.http_client import HTTPClient
from sigas_alpha.game.message.Message import MessageWithClientId, register_message_type


class HeloMessage(MessageWithClientId):
    @classmethod
    def from_body(cls, typ: str, body: bytes) -> 'HeloMessage': return HeloMessage(cls.extract_client_id(body))

    def __init__(self, client_id: str = "--"):
        super().__init__("HELO", client_id)


class PingPongMessage(MessageWithClientId, ABC):
    @classmethod
    def from_body(cls, typ: str, body: bytes) -> 'PingPongMessage':
        if typ == "PING":
            real_cls = PingMessage
        else:
            real_cls = PongMessage
        return real_cls(cls.extract_client_id(body), struct.unpack(">q", body[2:])[0] / 1000.0)

    def __init__(self, typ: str, client_id: str = "--", time: float = time.time()):
        super().__init__(typ, client_id)
        self.time = time

    def body(self) -> bytes:
        return self.client_id.encode("ascii") + struct.pack(">q", int(self.time * 1000))

    def __repr__(self) -> str:
        return f"{self.typ}({self.client_id}, {self.time})"


class PingMessage(PingPongMessage):
    def __init__(self, client_id: str = "--", time: float = time.time()):
        super().__init__("PING", client_id, time)


class PongMessage(PingPongMessage):
    def __init__(self, client_id: str = "--", time: float = time.time()):
        super().__init__("PONG", client_id, time)


register_message_type("HELO", HeloMessage)
register_message_type("PING", PingMessage)
register_message_type("PONG", PongMessage)


requests.post('http://localhost:8082/game/333', data='{"master_token": "1234", "client_id": "01"}')
requests.post('http://localhost:8082/game/333/client', data='{"token": "1235", "client_id": "02"}')
requests.post('http://localhost:8082/game/333/client', data='{"token": "1236", "client_id": "03"}')

requests.put('http://localhost:8082/game/333/start', data='')

master_http_client = HTTPClient("http://localhost:8081/game", "333", "1234").start()
client1_http_client = HTTPClient("http://localhost:8081/game", "333", "1235").start()
client2_http_client = HTTPClient("http://localhost:8081/game", "333", "1236").start()

def receive_messages(client: HTTPClient, connection_type: str):
    while True:
        msg = client.get_message(True)
        client_id = "-"
        if isinstance(msg, MessageWithClientId):
            client_id = msg.client_id
        print(f"{connection_type}: Client {client_id} received message {msg}")


Thread(target=receive_messages, args=[master_http_client, "master"], daemon=True).start()
Thread(target=receive_messages, args=[client1_http_client, "client1"], daemon=True).start()
Thread(target=receive_messages, args=[client2_http_client, "client2"], daemon=True).start()

time.sleep(1)


def message_generator(messages: list[bytes]):
    for msg in messages:
        yield msg


now = int(time.time() * 1000)


# print("Sending messages as client 1235")
# master_http_client.send_message(HeloMessage("01"))
# master_http_client.send_message(PingMessage("01"))
# print("Sent messages as client 1235")

print("Sending messages as client 1235")
client1_http_client.send_message(HeloMessage())
client1_http_client.send_message(PingMessage())
print("Sent messages as client 1235")

time.sleep(1)

print("Sending messages as master 1234")
master_http_client.send_message(PongMessage("02"))
print("Sent messages as master 1234")

print("Waiting 10s...")
time.sleep(10)
print("Done")

sys.exit(0)
