import time
from logging import getLogger
from threading import Thread
from typing import Optional

import waitress
from waitress.server import MultiSocketServer, create_server

from sigas_server_hub.flask_apps import app_external, app_internal, set_hub

from sigas_server_hub.actions.game_actions import GameActions
from sigas_server_hub.actions.status_actions import StatusActions
from sigas_server_hub.actions.token_actions import TokenActions
from sigas_server_hub.actions.user_actions import UserActions
from sigas_server_hub.sessions import SessionManager
from sigas_server_hub.tokens import TokenManager
from sigas_server_hub.users import UserManager

logger = getLogger(__name__)


class SigasHub:
    def __init__(self,
                 # app_external: Flask,
                 # app_internal: Flask,
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

        self.external_thread: Optional[Thread] = None
        self.internal_thread: Optional[Thread] = None
        self.external_server: Optional[MultiSocketServer] = None
        self.internal_server: Optional[MultiSocketServer] = None
        self.running = False

        StatusActions(self.game_manager)
        TokenActions(self.token_manager)
        UserActions(self.session_manager)
        GameActions(self.game_manager, self.user_manager)

        set_hub(self)

    def start(self) -> None:

        self.external_server = create_server(self.app_external, host="0.0.0.0", port=self.external_port)
        self.internal_server = create_server(self.app_internal, host="0.0.0.0", port=self.internal_port)

        def run_server(server: MultiSocketServer) -> None:
            try:
                server.run()
            except:
                pass

        self.running = True
        logger.info(f"Starting server at port {self.external_port}...")
        self.external_thread = Thread(target=run_server, args=[self.external_server], daemon=True)
        self.external_thread.start()
        logger.info(f"Started server at port {self.external_port}.")

        logger.info(f"Starting server at port {self.internal_port}...")
        self.internal_thread = Thread(target=run_server, args=[self.internal_server], daemon=True)
        self.internal_thread.start()
        logger.info(f"Started server at port {self.internal_port}.")

        last_expunge_checked = time.time()
        try:
            while self.running:  # Add option for this to be completed
                time.sleep(1)
                if self.expunge_interval <= time.time() - last_expunge_checked:
                    self.token_manager.check_for_expunge()
                    self.user_manager.check_for_expunge()
                    last_expunge_checked = time.time()
        finally:
            logger.warning("Finished Hub loop")

    def stop(self) -> None:
        self.running = False
        if self.external_server is not None:
            self.external_server.close()
        if self.internal_server is not None:
            self.internal_server.close()
        if self.external_thread is not None:
            self.external_thread.join(1)
        if self.internal_thread is not None:
            self.internal_thread.join(1)
        self.game_manager.close_server()
