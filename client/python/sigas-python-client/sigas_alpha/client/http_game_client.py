import logging
import struct
import time
from queue import Queue, Empty
from threading import Thread
from typing import Any, Generator, Optional

import requests

from sigas_alpha.game.game import Game
from sigas_alpha.game.message.Message import create_message, T_EXTENDS_MESSAGE
from sigas_alpha.player import Player

logger = logging.getLogger(__name__)


class HTTPGameClient:
    def __init__(self, api_url: str, api_token: str) -> None:
        self.api_url = api_url
        self.api_token = api_token
        self.stream_url = ""
        self.stream_token = ""
        self._sending_thread = None
        self._sending_thread_running = False
        self._receiving_thread = None
        self._receiving_thread_running = False
        self._do_run = False
        self._send_queue: Queue[T_EXTENDS_MESSAGE] = Queue()
        self._receive_queue: Queue[T_EXTENDS_MESSAGE] = Queue()
        self.game: Optional[Game] = None
        self.player: Optional[Player] = None

    def create_game(self, game_name: str, alias: Optional[str] = None) -> tuple[Game, Player]:
        request_body = {
            "name": game_name,
            **({"alias": alias} if alias is not None else {})
        }

        response = requests.post(f"{self.api_url}/game", headers={"Authorization": f"Token {self.api_token}"}, json=request_body)

        response_body = response.json()

        game_id = response_body["game_id"]
        game_name = response_body["game_name"]
        self.stream_url = response_body["url"]
        if self.stream_url.startswith("/"):
            self.stream_url = self.api_url + self.stream_url

        response_player_body = response_body["master_player"]
        player_id = response_player_body["player_id"]
        alias = response_player_body["alias"]
        self.stream_token = response_player_body["token"]
        self.player = Player(player_id, alias)

        self.game = Game(game_id, game_name, self.stream_url)
        self.game.master_player = self.player
        return self.game, self.player

    def join_game(self, game_id: str, alias: Optional[str] = None) -> tuple[Game, Player]:
        request_body = {
            "game_id": game_id,
            **({"alias": alias} if alias is not None else {})
        }

        response = requests.post(f"{self.api_url}/game/{game_id}/join", headers={"Authorization": f"Token {self.api_token}"}, json=request_body)

        response_body = response.json()

        game_name = response_body["game_name"]
        self.stream_url = response_body["url"]
        if self.stream_url.startswith("/"):
            self.stream_url = self.api_url + self.stream_url

        player_body = response_body["player"]

        self.stream_token = player_body["token"]
        self.player = Player(player_body["player_id"], player_body["alias"])

        self.game = Game(game_id, game_name, self.stream_url)
        return self.game, self.player

    def start_game(self) -> 'HTTPGameClient':
        if self.game is None:
            raise ValueError("Need to create game first")

        # TODO remove body
        response = requests.post(f"{self.api_url}/game/{self.game.game_id}/join", headers={"Authorization": f"Token {self.api_token}"}, json={})

    def start_stream(self) -> 'HTTPGameClient':
        if self.game is None:
            raise ValueError("Need to create or join game first")

        self._do_run = True

        url = f"{self.stream_url}"

        self._sending_thread = Thread(target=self._outbound_connection_loop, args=[url, self.stream_token], daemon=True)
        self._receiving_thread = Thread(target=self._inbound_connection_loop, args=[url, self.stream_token], daemon=True)

        self._sending_thread.start()
        self._receiving_thread.start()
        return self

    def stop_stream(self, wait: float = 0.0) -> 'HTTPGameClient':
        self._do_run = False
        if wait > 0.0:
            self._sending_thread.join(wait)
            self._receiving_thread.join(wait)

            now = time.time()
            while time.time() - now < wait and (self._sending_thread_running or self._receiving_thread_running):
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
            logger.warning(f"{token}: Finished outbound streaming loop")
            self._sending_thread_running = False

    def _message_generator(self) -> Generator[bytes, Any, None]:
        while self._do_run:
            try:
                message = self._send_queue.get(True, 0.1)
                if message is not None:
                    body = message.body()
                    complete_message = message.typ.encode("ASCII") + message.flags.encode("ASCII") + message.client_id.encode("ASCII") + struct.pack(">i", len(body)) + body
                    yield complete_message
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
                        flags = chunk[4:6].decode("ASCII")
                        client_id = chunk[6:8].decode("ASCII")
                        l = struct.unpack(">i", chunk[4:8])[0]
                        body = chunk[12:12 + l]

                        message = create_message(typ, client_id, flags, body)
                        self._receive_queue.put(message)
                except Exception as e:
                    if self._do_run:
                        logger.warning(f"Got exception in inbound loop; {e}", exc_info=True)
        finally:
            logger.warning(f"{token}: Finished inbound streaming loop")
            self._receiving_thread_running = False

    def send_message(self, message: T_EXTENDS_MESSAGE) -> 'HTTPGameClient':
        self._send_queue.put(message)
        return self

    def get_message(self, block: bool = True, timeout: float = None) -> Optional[T_EXTENDS_MESSAGE]:
        try:
            return self._receive_queue.get(block, timeout)
        except Empty:
            return None
