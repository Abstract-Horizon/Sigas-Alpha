import threading
import time
from logging import getLogger

import waitress
from flask import Flask

from sigas_server_hub.actions.game_actions import GameActions
from sigas_server_hub.actions.status_actions import StatusActions
from sigas_server_hub.actions.token_actions import TokenActions
from sigas_server_hub.actions.user_actions import UserActions
from sigas_server_hub.game.game_manager import GameManager
from sigas_server_hub.sessions import SessionManager
from sigas_server_hub.tokens import TokenManager
from sigas_server_hub.users import UserManager

logger = getLogger(__name__)


class SigasHub:
    def __init__(self,
                 app_external: Flask,
                 app_internal: Flask,
                 external_port: int,
                 internal_port: int,
                 token_file: str,
                 users_file: str,
                 expunge_trigger_ratio: float,
                 expunge_interval: float,
                 game_manager_class: type) -> None:

        self.app_external = app_external
        self.app_internal = app_internal

        self.external_port = external_port
        self.internal_port = internal_port

        self.expunge_trigger_ratio = expunge_trigger_ratio
        self.expunge_interval = expunge_interval

        self.token_manager = TokenManager(token_file, expunge_trigger_ratio=expunge_trigger_ratio)
        self.user_manager = UserManager(users_file, expunge_trigger_ratio=expunge_trigger_ratio)

        self.user_manager.load_users()
        self.token_manager.load_tokens()

        self.session_manager = SessionManager()
        self.game_manager = game_manager_class()

        self.running = False

        StatusActions(self.game_manager)
        TokenActions(self.token_manager)
        UserActions(self.session_manager)
        GameActions(self.game_manager)

    def start(self) -> None:
        self.running = True
        logger.info(f"Starting server at port {self.external_port}...")
        threading.Thread(
            target=lambda: waitress.serve(self.app_external, host="0.0.0.0", port=self.external_port), daemon=True
        ).start()
        logger.info(f"Started server at port {self.external_port}.")

        logger.info(f"Starting server at port {self.internal_port}...")
        threading.Thread(
            target=lambda: waitress.serve(self.app_internal, host="0.0.0.0", port=self.internal_port), daemon=True
        ).start()
        logger.info(f"Started server at port {self.internal_port}.")

        last_expunge_checked = time.time()
        while self.running:  # Add option for this to be completed
            time.sleep(1)
            if self.expunge_interval <= time.time() - last_expunge_checked:
                self.token_manager.check_for_expunge()
                self.user_manager.check_for_expunge()
                last_expunge_checked = time.time()

    def stop(self) -> None:
        self.running = False
