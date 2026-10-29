from apexcraft.agent.task import TaskStatus, TERMINAL


class TaskScheduler:
    """Stable priority scheduling; one bounded action tick per controller tick."""
    def __init__(self):
        self.tasks = []
        self.current = None
        self.paused = False

    def submit(self, task):
        def flatten(node):
            result = [node]
            for child in node.children:
                result.extend(flatten(child))
            return result
        incoming = flatten(task)
        ids = [t.id for t in self.tasks + incoming]
        if len(ids) != len(set(ids)):
            raise ValueError('Task IDs must be unique')
        self.tasks.extend(incoming)

    def tick(self, world):
        if self.paused:
            return
        for task in self.tasks:
            if task.status not in TERMINAL and task.token.cancelled:
                task.cancel()
            elif task.status not in TERMINAL and any(
                c.status in {TaskStatus.FAILED, TaskStatus.CANCELLED} for c in task.children
            ):
                task.tick(world)
        eligible = [t for t in self.tasks if t.can_start() and t.status != TaskStatus.BLOCKED]
        chosen = max(eligible, key=lambda t: t.priority, default=None)
        if self.current is not None and self.current is not chosen:
            self.current.suspend()
        self.current = chosen
        if chosen:
            chosen.tick(world)

    def pause(self):
        self.paused = True
        if self.current:
            self.current.suspend()
        self.current = None

    def resume(self):
        self.paused = False

    def stop(self):
        errors = []
        try:
            self.pause()
        except Exception as error:
            errors.append(error)
        for task in self.tasks:
            try:
                task.cancel()
            except Exception as error:
                errors.append(error)
        if errors:
            raise ExceptionGroup('Input cleanup failed', errors)
