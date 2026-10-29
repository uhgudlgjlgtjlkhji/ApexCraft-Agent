import json
import logging
from dataclasses import asdict, is_dataclass


class DecisionTrace:
    """Attach to a bus without coupling production tasks to a console UI."""
    def __init__(self, logger: logging.Logger):
        self.logger = logger

    def record(self, event: object) -> None:
        payload = asdict(event) if is_dataclass(event) else {'value': str(event)}
        self.logger.info(json.dumps({'event': type(event).__name__, **payload}, default=str))
