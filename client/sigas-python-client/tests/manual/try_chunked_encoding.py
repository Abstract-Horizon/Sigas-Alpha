import requests
import time
import struct
from threading import Thread

requests.post('http://localhost:8082/game/333', data='{"master_token": "1234", "client_id": "01"}')
requests.post('http://localhost:8082/game/333/client', data='{"token": "1235", "client_id": "02"}')
requests.post('http://localhost:8082/game/333/client', data='{"token": "1236", "client_id": "03"}')

requests.put('http://localhost:8082/game/333/start', data='')


def create_message(typ: str, body: bytes) -> bytes:
    return typ.encode("ascii") + struct.pack(">i", len(body)) + body


def receive_master_input(master_id: str):
    r = requests.get(f"http://localhost:8081/game/333/{master_id}", data='', stream=True)

    for chunk in (r.raw.read_chunked()):
        typ = chunk[0:4].decode("ASCII")
        len = struct.unpack(">i", chunk[4:8])[0]
        body = chunk[8:8 + len]
        client_id = body[:2].decode('ascii')
        body = body[2:]
        print(f"Master received message {typ} ({len}) from {client_id}; body='{body}'")


def receive_client_input(client_id: str):
    r = requests.get(f"http://localhost:8081/game/333/{client_id}", data='', stream=True)

    for chunk in (r.raw.read_chunked()):
        typ = chunk[0:4].decode("ASCII")
        len = struct.unpack(">i", chunk[4:8])[0]
        body = chunk[8:8 + len]
        _received_client_id = str(body[:2])
        body = body[2:]
        print(f"Client {client_id} received message {typ} ({len}) from master; body='{body}'")


client1_thread = Thread(target=receive_master_input, args=["1234"])
client1_thread.start()


client1_thread = Thread(target=receive_client_input, args=["1235"])
client1_thread.start()


client1_thread = Thread(target=receive_client_input, args=["1236"])
client1_thread.start()

time.sleep(1)


def message_generator(messages: list[bytes]):
    for msg in messages:
        yield msg


now = int(time.time() * 1000)

print("Sending messages as client 1235")
requests.post('http://localhost:8081/game/333/1235', data=message_generator([
    create_message("HELO", b"\x00\x00"),
    create_message("PING", b"\x00\x00" + struct.pack(">q", now))
]))
print("Sent messages as client 1235")

time.sleep(1)

now = int(time.time() * 1000)
print("Sending messages as master 1234")
requests.post('http://localhost:8081/game/333/1234', data=message_generator([
    b"PONG" + struct.pack(">i", 10) + b"00" + struct.pack(">q", now)
]))
print("Sent messages as master 1234")
