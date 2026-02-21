from sigas_server_hub.actions.game_actions import GameActions
from sigas_server_hub.actions.status_actions import StatusActions
from sigas_server_hub.actions.token_actions import TokenActions
from sigas_server_hub.actions.user_actions import UserActions
from sigas_server_hub.game.game_manager import GameManager
from sigas_server_hub.sessions import SessionManager
from sigas_server_hub.tokens import TokenManager
from sigas_server_hub.users import UserManager


class AppContext:
    def __init__(self, token_file: str, users_file: str, expunge_trigger_ratio: float) -> None:
        self.token_manager = TokenManager(token_file, expunge_trigger_ratio=expunge_trigger_ratio)
        self.user_manager = UserManager(users_file, expunge_trigger_ratio=expunge_trigger_ratio)

        self.user_manager.load_users()
        self.token_manager.load_tokens()

        self.session_manager = SessionManager()
        self.game_manager = GameManager()

        StatusActions(self.game_manager)
        TokenActions(self.token_manager)
        UserActions(self.session_manager)
        GameActions(self.game_manager)
