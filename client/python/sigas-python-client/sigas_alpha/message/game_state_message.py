from typing import Any

from sigas_alpha.message.messages import JsonMessage


class GameStateMessage(JsonMessage):
    def __init__(self, json_body: dict[str, Any]) -> None:
        super().__init__(json_body)
