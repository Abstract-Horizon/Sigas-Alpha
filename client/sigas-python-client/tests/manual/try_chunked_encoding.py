import requests
import time
import struct
from threading import Thread

requests.post('http://localhost:8082/game/123', data='{"master_token": "1234"}')

requests.post('http://localhost:8082/game/123/client', data='{"token": "1235"}')

requests.put('http://localhost:8082/game/123/start', data='')


def message_generator():
    yield b"HELO\x00\x00\x00\x00"

    now = int(time.time() * 1000)
    print(f"now ms is {now}")
    yield b"PING" + struct.pack(">i", 11) + struct.pack(">q", now) + b"END"

    time.sleep(100)


def receive_master_input():
    r = requests.get("http://localhost:8081/game/123/1234", data='', stream=True)

    for chunk in (r.raw.read_chunked()):
        typ = chunk[0:4].decode("ASCII")
        len = struct.unpack(">i", chunk[4:8])[0]
        body = chunk[8:8 + len]
        print(f"Received message {typ} ({len}); body='{body}'")

thread = Thread(target=receive_master_input)
thread.start()

time.sleep(1)

requests.post('http://localhost:8081/game/123/1235', data=message_generator())

