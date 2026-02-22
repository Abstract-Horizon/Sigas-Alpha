import inspect
import random
import sys
from base64 import urlsafe_b64encode
from typing import Sequence

TOKEN_LENGTH = 12

HASH_LIMIT = 2 ** 48

# Permissions Type
Permissions = Sequence[str]



def fast_random_hash(size: int) -> str:
    return urlsafe_b64encode((hash(random.random()) % HASH_LIMIT).to_bytes(size, "big")).decode("UTF8").rstrip("=")


def find_class(func):
    cls = sys.modules.get(func.__module__)
    if cls is None:
        return None
    if "__qualname__" not in dir(func):
        return None
    for name in func.__qualname__.split(".")[:-1]:
        cls = getattr(cls, name)
    if not inspect.isclass(cls):
        return None
    return cls
