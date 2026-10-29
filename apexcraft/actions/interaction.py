"""Single interactions wait for a later observation before verification."""


class InteractionAction:
    def __init__(self, operation, predicate, release_inputs):
        self.operation, self.predicate = operation, predicate
        self.release_inputs = release_inputs
        self.sent_version = None
        self.done = False

    def start(self, token):
        self.token = token
        token.check()
        self.sent_version = None
        self.done = False

    def tick(self, world):
        self.token.check()
        if self.sent_version is None:
            self.operation()
            self.sent_version = world.version
        elif world.version > self.sent_version:
            self.done = True

    def is_complete(self):
        return self.done

    def verify(self, world):
        return bool(self.predicate(world))

    def cancel(self):
        self.release_inputs()
