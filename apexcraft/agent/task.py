from dataclasses import dataclass
from enum import Enum
from apexcraft.runtime.cancellation import CancellationToken, CancelledError


class TaskStatus(str, Enum):
    PENDING = 'PENDING'
    RUNNING = 'RUNNING'
    BLOCKED = 'BLOCKED'
    SUCCESS = 'SUCCESS'
    FAILED = 'FAILED'
    CANCELLED = 'CANCELLED'


TERMINAL = {TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.CANCELLED}


@dataclass(frozen=True)
class TaskChanged:
    task_id: str
    status: TaskStatus
    reason: str | None


class Task:
    """One action plus child prerequisites; success always requires observation."""
    def __init__(self, task_id, action, events, *, priority=0, max_ticks=200, max_retries=0):
        if max_ticks < 1 or max_retries < 0:
            raise ValueError('Invalid task budgets')
        self.id, self.action, self.events = task_id, action, events
        self.priority, self.max_ticks, self.max_retries = priority, max_ticks, max_retries
        self.status = TaskStatus.PENDING
        self.failure_reason = None
        self.parent = None
        self.children = []
        self.token = CancellationToken()
        self.ticks = self.retries = 0
        self._started = False

    def add_child(self, child):
        if self.status != TaskStatus.PENDING or child.status != TaskStatus.PENDING:
            raise ValueError('Only pending tasks may be attached')
        node = self
        while node:
            if node is child:
                raise ValueError('Task hierarchy cycle')
            node = node.parent
        if child.parent is not None:
            raise ValueError('Task already has a parent')
        child.parent = self
        child.token.parent = self.token
        self.children.append(child)

    def _transition(self, status, reason=None):
        self.status, self.failure_reason = status, reason
        self.events.publish(TaskChanged(self.id, status, reason))

    def can_start(self):
        return (not self.token.cancelled and self.status not in TERMINAL and self.status != TaskStatus.BLOCKED
                and all(c.status == TaskStatus.SUCCESS for c in self.children))

    def start(self):
        if not self.can_start():
            raise RuntimeError('Task prerequisites not satisfied')
        if not self._started:
            self._started = True
            self.action.start(self.token)
        self._transition(TaskStatus.RUNNING)

    def tick(self, world):
        if self.status in TERMINAL or self.status == TaskStatus.BLOCKED:
            return
        try:
            self.token.check()
            if any(c.status in {TaskStatus.FAILED, TaskStatus.CANCELLED} for c in self.children):
                self.action.cancel()
                self._transition(TaskStatus.FAILED, 'Child prerequisite failed')
                return
            if not self.can_start():
                self._transition(TaskStatus.BLOCKED, 'Waiting for prerequisites')
                return
            if self.status != TaskStatus.RUNNING:
                self.start()
            self.ticks += 1
            self.action.tick(world)
            self.token.check()
            if self.action.is_complete():
                if self.verify(world):
                    self.action.cancel()
                    self._transition(TaskStatus.SUCCESS)
                else:
                    self.action.cancel()
                    self._transition(TaskStatus.BLOCKED, 'Result verification failed')
            elif self.ticks >= self.max_ticks:
                self.action.cancel()
                self._transition(TaskStatus.BLOCKED, 'Tick budget exceeded')
        except CancelledError:
            self.cancel()
        except Exception as error:
            try:
                self.action.cancel()
            finally:
                self._transition(TaskStatus.FAILED, f'{type(error).__name__}: {error}')

    def verify(self, world):
        return self.action.verify(world)

    def is_complete(self):
        return self.status == TaskStatus.SUCCESS

    def get_failure_reason(self):
        return self.failure_reason

    def recover(self, replacement):
        if self.status != TaskStatus.BLOCKED or self.retries >= self.max_retries:
            raise RuntimeError('Recovery unavailable or exhausted')
        self.action.cancel()
        self.action = replacement
        self.retries += 1
        self.ticks = 0
        self._started = False
        self._transition(TaskStatus.PENDING)

    def suspend(self):
        if self.status == TaskStatus.RUNNING:
            self.action.cancel()
            self._started = False
            self._transition(TaskStatus.PENDING)

    def cancel(self):
        if self.status in TERMINAL:
            return
        self.token.cancel()
        errors = []
        for cleanup in [self.action.cancel, *(child.cancel for child in self.children)]:
            try:
                cleanup()
            except Exception as error:
                errors.append(error)
        self._transition(TaskStatus.CANCELLED, 'Cancelled')
        if errors:
            raise ExceptionGroup('Task cleanup failed', errors)
