import json
import os.path
import time
from datetime import datetime, UTC
from typing import Optional

from sigas_server_hub.utils import fast_random_hash, TOKEN_LENGTH, Permissions


class Token:
    def __init__(self,
                 lifespan: float,
                 permissions: Optional[Permissions] = None,
                 note: str = "",
                 user_id: Optional[str] = None,
                 temporary: bool = False) -> None:
        self._token = fast_random_hash(TOKEN_LENGTH)
        self._created_at = time.time()
        self._lifespan = lifespan
        self._valid = True
        self._temporary = temporary
        self.permissions: frozenset[str] = frozenset(permissions) if permissions is not None else {}
        self._note = note
        self._user_id = user_id

    @property
    def is_temporary(self) -> bool:
        return self._temporary

    @property
    def token(self) -> str:
        return self._token

    @property
    def created_at(self) -> float:
        return self._created_at

    @property
    def is_valid(self) -> bool:
        if not self._valid:
            return False

        return self._created_at + self._lifespan >= time.time()

    @property
    def note(self) -> str:
        return self._note

    @property
    def user_id(self) -> Optional[str]:
        return self._user_id

    def as_json(self) -> dict:
        return {
            "token": self._token,
            "created_at": datetime.fromtimestamp(self._created_at, UTC).isoformat(),
            "lifespan": self._lifespan,
            "valid": self._valid,
            "note": self._note,
            "permissions": list(self.permissions),
            **({"temporary": True} if self._temporary else {}),
            **({"user_id": True} if self._user_id is not None else {})
        }

    @classmethod
    def from_json(cls, d: dict) -> 'Token':
        token = Token(d["lifespan"], permissions=d["permissions"], note=d["note"])
        token._token = d["token"]
        token._created_at = datetime.fromisoformat(d["created_at"]).timestamp()
        token._valid = d["valid"]
        token._user_id = d["user_id"] if "user_id" in d else None

        return token

    def __repr__(self) -> str:
        return (f"Token("
                f"{self.token}, {datetime.fromtimestamp(self._created_at, UTC).isoformat()}, "
                f"ls={self._lifespan}s, temp={self._temporary}, valid={self._valid}, "
                f"note='{self.note}', "
                f"p={self.permissions})")


class TokenManager:
    def __init__(self, token_file: str, expunge_trigger_ratio: float = 1) -> None:
        self.token_file = token_file

        # percentage when to trigger re-writing of token file to remove
        # duplicates and/or expired tokens
        self.expunge_trigger_ratio = expunge_trigger_ratio

        self.tokens: dict[str, Token] = {}
        self.duplicates_and_invalid_tokens = 0

    def create_token(self,
                     lifespan: float,
                     permissions: Optional[Permissions] = None,
                     note: str = "",
                     temporary: bool = False) -> Token:
        token = Token(lifespan, permissions=permissions, note=note, temporary=temporary)
        self.tokens[token.token] = token
        if not temporary:
            self._save_token(token)
        return token

    def get_token(self, token: str) -> Optional[Token]:
        if token in self.tokens:
            return self.tokens[token]
        return None

    def invalidate_token(self, token: Token) -> None:
        token._valid = False
        del self.tokens[token.token]
        if not token.is_temporary and token.token in self.tokens:
            self.duplicates_and_invalid_tokens += 1

    def update_note(self, token: Token, note: str) -> None:
        if token.token not in self.tokens:
            raise KeyError(f"Can't find token {token.token}")

        if token.note != note:
            token._note = note
            if not token.is_temporary:
                self.duplicates_and_invalid_tokens += 1

    def load_tokens(self) -> None:
        tokens: dict[str, Token] = {}
        duplicates_and_invalid_tokens = 0
        if os.path.exists(self.token_file):
            with open(self.token_file, "rt") as f:
                line = f.readline()
                while line != "":
                    token = Token.from_json(json.loads(line))

                    if token.token in self.tokens or not token.is_valid:
                        duplicates_and_invalid_tokens += 1

                    if token.is_valid:
                        tokens[token.token] = token

                    line = f.readline()

        self.tokens = tokens | {t.token: t for t in self.tokens.values() if t.is_temporary}
        self.duplicates_and_invalid_tokens = duplicates_and_invalid_tokens
        if len(self.tokens) > 0 and duplicates_and_invalid_tokens / len(self.tokens) > self.expunge_trigger_ratio:
            self._save_tokens()

    def _save_token(self, token: Token) -> None:
        with open(self.token_file, "at") as f:
            f.write(json.dumps(token.as_json()))
            f.write("\n")

    def _save_tokens(self) -> None:
        self.tokens = {token.token: token for token in self.tokens.values() if token.is_valid}
        with open(self.token_file, "wt") as f:
            for token in self.tokens.values():
                if token.is_valid and not token.is_temporary:
                    f.write(json.dumps(token.as_json()))
                    f.write("\n")

        self.duplicates_and_invalid_tokens = 0

    def check_for_expunge(self) -> bool:
        if len(self.tokens) > 0 and self.duplicates_and_invalid_tokens / len(self.tokens) > self.expunge_trigger_ratio:
            self._save_tokens()
            return True
        return False
