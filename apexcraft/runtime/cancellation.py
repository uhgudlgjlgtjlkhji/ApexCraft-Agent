"""Cooperative cancellation with downward-only propagation."""


class CancelledError(Exception):
    pass


class CancellationToken:
    def __init__(self, parent: 'CancellationToken | None' = None):
        self.parent = parent
        self._cancelled = False

    @property
    def cancelled(self) -> bool:
        return self._cancelled or bool(self.parent and self.parent.cancelled)

    def cancel(self) -> None:
        self._cancelled = True

    def check(self) -> None:
        if self.cancelled:
            raise CancelledError('Automation cancelled')
