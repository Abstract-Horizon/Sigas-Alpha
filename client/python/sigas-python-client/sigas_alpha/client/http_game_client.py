import logging
import struct
import time
from queue import Queue, Empty
from threading import Thread
from typing import Any, Generator, Optional, Callable

import requests

from sigas_alpha.game.game import Game, GameOptions
from sigas_alpha.message import create_message, MessageExtension, HeloMessage, HeartBeatMessage, ClientReconnectedMessage, JoinedMessage
from sigas_alpha.message.system_messages import PlayerListMessage
from sigas_alpha.player import Player

logger = logging.getLogger(__name__)


class StreamPair:
    def __init__(self,
                 http_game_client: 'HTTPGameClient',
                 max_graceful_backoff_period: float = 2,
                 first_graceful_backoff_period: float = 0.02) -> None:
        self.http_game_client = http_game_client
        self.max_graceful_backoff_period = max_graceful_backoff_period
        self.first_graceful_backoff_period = first_graceful_backoff_period
        self._inbound_graceful_backoff_period = 0
        self._outbound_graceful_backoff_period = 0

        self._do_run = False
        self._sending_thread = None
        self._sending_thread_running = False
        self._heartbeat_thread = None
        self._heartbeat_thread_running = False
        self._receiving_thread = None
        self._receiving_thread_running = False

        self._first_heartbeat = True

    def stop(self, wait: float = 0.0) -> None:
        self._do_run = False
        if wait > 0.0:
            self._sending_thread.join(wait)
            self._receiving_thread.join(wait)

            now = time.time()
            while time.time() - now < wait and (self._sending_thread_running or self._receiving_thread_running or self._heartbeat_thread_running):
                time.sleep(0.1)
            if self._sending_thread_running:
                print(f"Stopped connection but sending thread is still running")
            if self._receiving_thread_running:
                print(f"Stopped connection but receiving thread is still running")
            if self._heartbeat_thread_running:
                print(f"Stopped connection but heartbeat thread is still running")

        else:
            self._sending_thread.join(0.001)
            self._heartbeat_thread.join(0.001)
            self._receiving_thread.join(0.001)

    def start(self) -> None:
        self._do_run = True

        self._first_heartbeat = True

        url = f"{self.http_game_client.stream_url}"

        self._sending_thread = Thread(target=self._outbound_connection_loop, args=[url, self.http_game_client.stream_token], daemon=True)
        self._heartbeat_thread = Thread(target=self._heartbeat_generator, args=[], daemon=True)
        self._receiving_thread = Thread(target=self._inbound_connection_loop, args=[url, self.http_game_client.stream_token], daemon=True)

        self._sending_thread.start()
        self._heartbeat_thread.start()
        self._receiving_thread.start()

    def _outbound_connection_loop(self, url: str, token: str) -> None:
        self._sending_thread_running = True
        self._outbound_graceful_backoff_period = 0
        _last_request_time = time.time()
        try:
            while self._do_run:
                try:
                    _last_request_time = time.time()
                    requests.post(url, headers={"Authorization": f"Token {token}", "Transfer-Encoding": "chunked"}, data=self._message_generator())
                except Exception as e:
                    logger.warning(f"Got exception in outbound loop; {e}", exc_info=True)
                    if time.time() - _last_request_time < self.max_graceful_backoff_period:
                        if self._outbound_graceful_backoff_period < self.first_graceful_backoff_period:
                            self._outbound_graceful_backoff_period = self.first_graceful_backoff_period
                        else:
                            self._outbound_graceful_backoff_period *= 2
                            if self._outbound_graceful_backoff_period > self.max_graceful_backoff_period:
                                self._outbound_graceful_backoff_period = self.max_graceful_backoff_period
                        logger.warning(f"Backing off outbound request for {self._outbound_graceful_backoff_period}")
                        time.sleep(self._outbound_graceful_backoff_period)

        finally:
            logger.warning(f"{token}:{self.http_game_client.player.player_id}: Finished outbound streaming loop")
            self._sending_thread_running = False

    def _message_generator(self) -> Generator[bytes, Any, None]:
        while self._do_run:
            try:
                message = self.http_game_client._send_queue.get(True, 0.1)
                if message is not None:
                    body = message.body()
                    complete_message = message.typ.encode("ASCII") + message.flags.encode("ASCII") + message.client_id.encode("ASCII") + struct.pack(">I", len(body)) + body
                    yield complete_message
            except Empty:
                pass
        if not self._do_run:
            # Send last heartbeat message so we receive something from the other end and close inbound queue
            message = HeartBeatMessage(self.http_game_client.heartbeat_next_sequence)
            body = message.body()
            complete_message = message.typ.encode("ASCII") + message.flags.encode("ASCII") + message.client_id.encode("ASCII") + struct.pack(">I", len(body)) + body
            yield complete_message

    def _heartbeat_generator(self) -> None:
        self._heartbeat_thread_running = True
        while self._do_run:
            time.sleep(self.http_game_client.heartbeat_period)
            now = time.time()
            if self._first_heartbeat:
                self._first_heartbeat = False
            elif not self.heartbeat_received:
                # TODO what to do if we haven't got
                self.http_game_client.broadcast_missed_heartbeat()

            self.last_heartbeat_time = now
            self.http_game_client.heartbeat_next_sequence += 1
            # TODO - this will break if we want to always receive 'next' bigger one
            if self.http_game_client.heartbeat_next_sequence > 32000:
                self.heartbeat_received = 1
            self.heartbeat_received = False
            self.http_game_client._send_queue.put(HeartBeatMessage(self.http_game_client.heartbeat_next_sequence))

    def _inbound_connection_loop(self, url: str, token: str) -> None:
        self._receiving_thread_running = True
        self._inbound_graceful_backoff_period = 0
        try:
            while self._do_run:
                try:
                    r = requests.get(url, headers={"Authorization": f"Token {token}", "Transfer-Encoding": "chunked"}, data='', stream=True)
                    for chunk in r.raw.read_chunked():
                        self._inbound_graceful_backoff_period = 0
                        typ = chunk[0:4].decode("ASCII")
                        flags = chunk[4:6].decode("ASCII")
                        client_id = chunk[6:8].decode("ASCII")
                        l = struct.unpack(">I", chunk[4:8])[0]
                        body = chunk[12:12 + l]

                        message = create_message(typ, client_id, flags, body)
                        if isinstance(message, HeartBeatMessage):
                            if self.http_game_client.heartbeat_next_sequence == message.sequence:
                                self.heartbeat_received = True
                            else:
                                # TODO what to do if we received older or newer message
                                pass

                        else:
                            self.http_game_client._receive_message(message)

                        if not self._do_run:
                            r.close()
                            logger.warning(f"{token}:{self.http_game_client.player.player_id}: Closed inbound connection")
                            break

                except Exception as e:
                    if self._do_run:
                        logger.warning(f"Got exception in inbound loop; {e}", exc_info=True)

                        if self._inbound_graceful_backoff_period < self.first_graceful_backoff_period:
                            self._inbound_graceful_backoff_period = self.first_graceful_backoff_period
                        else:
                            self._inbound_graceful_backoff_period *= 2.0
                            if self._inbound_graceful_backoff_period > self.max_graceful_backoff_period:
                                self._inbound_graceful_backoff_period = self.max_graceful_backoff_period

                        logger.warning(f"Backing off inbound request for {self._inbound_graceful_backoff_period}")
                        time.sleep(self._inbound_graceful_backoff_period)
                    else:
                        logger.warning(f"Got exception in inbound loop; {e}", exc_info=True)
        finally:
            logger.warning(f"{token}:{self.http_game_client.player.player_id}: Finished inbound streaming loop")
            self._receiving_thread_running = False


class Callbacks:
    def __init__(
            self,
            on_message_received: Optional[Callable[['HTTPGameClient', MessageExtension], None]] = None,
            on_missed_heartbeat: Optional[Callable[['HTTPGameClient'], None]] = None,
            on_request_for_complete_game_state: Optional[Callable[['HTTPGameClient', str], None]] = None,
            on_player_joined: Optional[Callable[['HTTPGameClient', Player], None]] = None,
    ) -> None:
        self.on_message_received = on_message_received
        self.on_missed_heartbeat = on_missed_heartbeat
        self.on_request_for_complete_game_state = on_request_for_complete_game_state
        self.on_player_joined = on_player_joined


class HTTPGameClient:
    def __init__(self,
                 api_url: str,
                 api_token: str,
                 heartbeat_period: float = 2.0,
                 callbacks: Callbacks = Callbacks()
                 ) -> None:
        self.api_url = api_url
        self.api_token = api_token
        self.stream_url = ""
        self.stream_token = ""

        self._stream_pair: Optional[StreamPair] = None

        self._send_queue: Queue[MessageExtension] = Queue()
        self._receive_queue: Queue[MessageExtension] = Queue()
        self.game_master = False
        self.game: Optional[Game] = None
        self.player: Optional[Player] = None

        self.heartbeat_next_sequence = 0
        self.heartbeat_period = heartbeat_period
        self.last_heartbeat_time = 0
        self.heartbeat_received = False

        self.callbacks = callbacks

    def broadcast_missed_heartbeat(self) -> None:
        if self.callbacks.on_missed_heartbeat is not None:
            self.callbacks.on_missed_heartbeat(self)

    def create_game(self, game_name: str, alias: Optional[str] = None, game_options: GameOptions = GameOptions()) -> tuple[Game, Player]:
        request_body = {
            "name": game_name,
            **({"alias": alias} if alias is not None else {}),
            "options": game_options.as_json()
        }

        self.heartbeat_period = game_options.heartbeat_period

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

        self.game = Game(game_id, game_name, self.stream_url, master=True, game_options=game_options)
        self.game.master_player = self.player
        self.game_master = True
        self.game.players[self.player.player_id] = self.player

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
        self.game.players[self.player.player_id] = self.player

        self._send_queue.put(HeloMessage())
        return self.game, self.player

    def start_game(self) -> 'HTTPGameClient':
        if self.game is None:
            raise ValueError("Need to create game first")

        # TODO remove body
        requests.post(f"{self.api_url}/game/{self.game.game_id}/start", headers={"Authorization": f"Token {self.api_token}"}, json={})
        return self

    def start_stream(self) -> 'HTTPGameClient':
        if self.game is None:
            raise ValueError("Need to create or join game first")

        self.last_heartbeat_time = time.time()
        self.heartbeat_received = False

        self._stream_pair = StreamPair(self)
        self._stream_pair.start()

        return self

    def reconnect_stream(self) -> None:
        if self._stream_pair is not None:
            self._stream_pair.stop()
        self.start_stream()

    def stop_stream(self, wait: float = 0.0) -> 'HTTPGameClient':
        if self._stream_pair is not None:
            self._stream_pair.stop(wait)

        return self

    def send_message(self, message: MessageExtension) -> 'HTTPGameClient':
        self._send_queue.put(message)
        return self

    def get_message(self, block: bool = True, timeout: float = None) -> Optional[MessageExtension]:
        try:
            return self._receive_queue.get(block, timeout)
        except Empty:
            return None

    def _receive_message(self, message: MessageExtension) -> None:
        if isinstance(message, JoinedMessage):
            player = Player(message.client_id, message.json_body["alias"])
            logger.warning(f"Client: {self.game.game_id}:{self.player.player_id}: received join for {player.player_id} ")
            self.game.players[message.client_id] = player
            if self.callbacks.on_player_joined is not None:
                self.callbacks.on_player_joined(self, player)

        if self.game.is_master() and (isinstance(message, JoinedMessage) or isinstance(message, ClientReconnectedMessage)):
            response_json = {"players": [{"player_id": p.player_id, "alias": p.alias} for p in self.game.players.values()]}
            player_list_message = PlayerListMessage(response_json, client_id=message.client_id)
            self.send_message(player_list_message)

        if isinstance(message, PlayerListMessage):
            message.apply_to_game(self.game)

        self._receive_queue.put(message)
        if self.callbacks.on_message_received is not None:
            self.callbacks.on_message_received(self, message)
