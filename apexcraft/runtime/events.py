"""Synchronous, typed events. Handler errors propagate to the publisher."""
from collections import defaultdict
from collections.abc import Callable
from typing import Any


class EventBus:
    def __init__(self):
        self._handlers: dict[type, list[Callable[[Any], None]]] = defaultdict(list)

    def subscribe(self, event_type: type, handler: Callable) -> Callable[[], None]:
        handlers = self._handlers[event_type]
        handlers.append(handler)
        def unsubscribe():
            if handler in handlers:
                handlers.remove(handler)
        return unsubscribe

    def publish(self, event: object) -> None:
        for handler in tuple(self._handlers[type(event)]):
            handler(event)
