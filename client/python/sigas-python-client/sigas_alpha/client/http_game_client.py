import logging
import struct
import time
from queue import Queue, Empty
from threading import Thread
from typing import Any, Generator, Optional

import requests

from sigas_alpha.game.message.Message import create_message, T_EXTENDS_MESSAGE


logger = logging.getLogger(__name__)


class HTTPGameClient:
    def __init__(self, url: str, game_id: str, token: str) -> None:
        self.url = url
        self.game_id = game_id
        self.token = token
        self._sending_thread = None
        self._sending_thread_running = False
        self._receiving_thread = None
        self._receive_thread_running = False
        self._do_run = False
        self._send_queue: Queue[T_EXTENDS_MESSAGE] = Queue()
        self._receive_queue: Queue[T_EXTENDS_MESSAGE] = Queue()

    def create_game(self) -> None:
        pass

    def start(self) -> 'HTTPGameClient':
        self._do_run = True

        url = f"{self.url}/stream/{self.game_id}"

        self._sending_thread = Thread(target=self._outbound_connection_loop, args=[url, self.token], daemon=True)
        self._receiving_thread = Thread(target=self._inbound_connection_loop, args=[url, self.token], daemon=True)

        self._sending_thread.start()
        self._receiving_thread.start()
        return self

    def stop(self, wait: bool = True) -> 'HTTPGameClient':
        self._do_run = False
        if wait:
            while self._sending_thread_running or self._receive_thread_running:
                time.sleep(0.1)

        return self

    def _outbound_connection_loop(self, url: str, token: str) -> None:
        self._sending_thread_running = True
        try:
            while self._do_run:
                try:
                    requests.post(url, headers={"Authorization": f"Token {token}", "Transfer-Encoding": "chunked"}, data=self._message_generator())
                except Exception as e:
                    logger.warning(f"Got exception in outbound loop; {e}", exc_info=True)
        finally:
            self._sending_thread_running = False

    def _message_generator(self) -> Generator[bytes, Any, None]:
        while self._do_run:
            try:
                message = self._send_queue.get(True, 10)
                body = message.body()
                yield message.typ.encode("ASCII") + struct.pack(">i", len(body)) + body
            except Empty:
                pass

    def _inbound_connection_loop(self, url: str, token: str) -> None:
        self._receiving_thread_running = True
        try:
            while self._do_run:
                try:
                    r = requests.get(url, headers={"Authorization": f"Token {token}", "Transfer-Encoding": "chunked"}, data='', stream=True)
                    for chunk in (r.raw.read_chunked()):
                        typ = chunk[0:4].decode("ASCII")
                        l = struct.unpack(">i", chunk[4:8])[0]
                        body = chunk[8:8 + l]

                        message = create_message(typ, body)
                        self._receive_queue.put(message)
                except Exception as e:
                    logger.warning(f"Got exception in inbound loop; {e}", exc_info=True)
        finally:
            self._receiving_thread_running = False

    def send_message(self, message: T_EXTENDS_MESSAGE) -> None:
        self._send_queue.put(message)

    def get_message(self, block: bool = True, timeout: float = None) -> Optional[T_EXTENDS_MESSAGE]:
        try:
            return self._receive_queue.get(block, timeout)
        except Empty:
            return None
