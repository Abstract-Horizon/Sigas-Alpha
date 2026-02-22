import json
import os
import random
import time
from typing import Optional

from sigas_server_hub.utils import fast_random_hash, TOKEN_LENGTH, Permissions


class User:
    def __init__(self, username: str, permissions: Optional[Permissions] = None) -> None:
        self._user_id = fast_random_hash(TOKEN_LENGTH)
        self._created_at = time.time()
        self._username = username
        self._password_sha: Optional[str] = None
        self._email: Optional[str] = None
        self.permissions: frozenset[str] = frozenset(permissions) if permissions is not None else {}
        self._verified = False
        self._enabled = True

    @property
    def user_id(self) -> str:
        return self._user_id

    @property
    def username(self) -> str:
        return self._username

    @property
    def email(self) -> str:
        return self._email

    @property
    def is_enabled(self) -> bool:
        return self._enabled

    @property
    def is_verified(self) -> bool:
        return self._verified

    @property
    def created_at(self) -> float:
        return self._created_at

    def test_password(self, password_sha: str) -> bool:
        return self._password_sha == password_sha

    def as_json(self) -> dict:
        return {
            "user_id": self._user_id,
            "username": self._username,
            "password_sha": self._password_sha,
            "email": self._email,
            "created_at": self._created_at,
            "enabled": self._enabled,
            "verified": self._verified,
            "permissions": list(self.permissions)
        }

    @classmethod
    def from_json(cls, d: dict) -> 'User':
        user = User(d["username"], d["permissions"])
        user._user_id = d["user_id"]
        user._password_sha = d["password_sha"]
        user._email = d["email"]
        user._created_at = d["created_at"]
        user._enabled = d["enabled"]
        user._verified = d["verified"]

        return user

    def __repr__(self) -> str:
        return f"User({self._user_id}, {self.username}, email={self._email}, p={self.permissions})"


class UserManager:
    def __init__(self, users_file: str, expunge_trigger_ratio: float = 1) -> None:
        self.users_file = users_file
        self.users: dict[str, User] = {}

        self.expunge_trigger_ratio = expunge_trigger_ratio
        self.duplicates_and_invalid_users = 0
        self.anonymous_number = random.randint(123, 5428)

    def create_user(self,
                    username: str,
                    password_sha: str,
                    email: Optional[str] = None,
                    permissions: Optional[set[str]] = None) -> User:
        user = User(username, permissions)
        user._password_sha = password_sha
        user._email = email

        self.users[user.user_id] = user
        self._save_user(user)
        return user

    def get_user(self, user_id: str) -> Optional[User]:
        if user_id in self.users:
            return self.users[user_id]

        return None

    def change_password(self, user: User, new_password_sha: str) -> None:
        if user.user_id not in self.users:
            raise KeyError(f"Can't find user {user}")

        if not user.test_password(new_password_sha):
            user._password_sha = new_password_sha
            self._save_user(user)
            self.duplicates_and_invalid_users += 1

    def change_email(self, user: User, new_email: str) -> None:
        if user.user_id not in self.users:
            raise KeyError(f"Can't find user {user}")

        if user.email != new_email:
            user._email = new_email
            self._save_user(user)
            self.duplicates_and_invalid_users += 1

    def update_permissions(self, user: User, permissions: Optional[set[str]] = None) -> None:
        if user.user_id not in self.users:
            raise KeyError(f"Can't find user {user}")

        if user.permissions != permissions:
            user.permissions = frozenset(permissions)
            self._save_user(user)
            self.duplicates_and_invalid_users += 1

    def enable(self, user: User) -> None:
        self._update_enabled(user, True)

    def disable(self, user: User) -> None:
        self._update_enabled(user, False)

    def _update_enabled(self, user: User, enabled: bool) -> None:
        if user.user_id not in self.users:
            raise KeyError(f"Can't find user {user}")

        if user.is_enabled != enabled:
            user._enabled = enabled
            self._save_user(user)
            self.duplicates_and_invalid_users += 1

    def load_users(self) -> None:
        users: dict[str, User] = {}
        duplicates_and_invalid_users = 0
        if os.path.exists(self.users_file):
            with open(self.users_file, "rt") as f:
                line = f.readline()
                while line != "":
                    user = User.from_json(json.loads(line))

                    if user.user_id in self.users:
                        duplicates_and_invalid_users += 1

                    users[user.user_id] = user

                    line = f.readline()

        self.users = users
        self.duplicates_and_invalid_users = duplicates_and_invalid_users
        if len(self.users) > 0 and duplicates_and_invalid_users / len(self.users) > self.expunge_trigger_ratio:
            self._save_users()

    def _save_user(self, user: User) -> None:
        with open(self.users_file, "at") as f:
            f.write(json.dumps(user.as_json()))
            f.write("\n")

    def _save_users(self) -> None:
        with open(self.users_file, "wt") as f:
            for user in self.users.values():
                f.write(json.dumps(user.as_json()))
                f.write("\n")

        self.duplicates_and_invalid_users = 0

    def check_for_expunge(self) -> bool:
        if len(self.users) > 0 and self.duplicates_and_invalid_users / len(self.users) > self.expunge_trigger_ratio:
            self._save_users()
            return True
        return False

    def next_anonymous_number(self) -> int:
        self.anonymous_number += 1
        return self.anonymous_number
