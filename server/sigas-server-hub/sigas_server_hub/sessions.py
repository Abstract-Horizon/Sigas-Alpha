import time
from typing import Optional

from sigas_server_hub.utils import fast_random_hash


class Session(dict):
    def __init__(self) -> None:
        super().__init__()
        self._last_used = time.time()
        self._session_id = fast_random_hash(12)

    @property
    def session_id(self) -> str:
        return self._session_id

    def touch(self) -> None:
        self._last_used = time.time()

    @property
    def last_used(self) -> float:
        return self._last_used


class SessionManager:
    def __init__(self, session_timeout: float = 300) -> None:
        self.all_sessions: dict[str, Session] = {}
        self.session_timeout = session_timeout

    def new_session(self) -> Session:
        session = Session()
        self.all_sessions[session.session_id] = session
        return session

    def get_session(self, session_hash: str) -> Optional[Session]:
        now = time.time()
        session: Optional[Session] = None
        hashes_to_remove: list[str] = []
        for s in self.all_sessions.values():
            if s.session_id == session_hash:
                session = s
            if s.last_used + self.session_timeout < now:
                hashes_to_remove.append(s.session_id)
                if s == session:
                    session = None

        for remove_hash in hashes_to_remove:
            del self.all_sessions[remove_hash]

        return session
