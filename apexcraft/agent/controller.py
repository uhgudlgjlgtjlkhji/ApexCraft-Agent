class AgentController:
    """Host supplies observation updates and ticks; no hidden threads or sleeps."""
    def __init__(self, world, scheduler, release_inputs):
        self.world, self.scheduler = world, scheduler
        self.release_inputs = release_inputs

    def tick(self):
        self.scheduler.tick(self.world.snapshot)

    def pause(self):
        try:
            self.scheduler.pause()
        finally:
            self.release_inputs()

    def resume(self):
        self.scheduler.resume()

    def stop(self):
        try:
            self.scheduler.stop()
        finally:
            self.release_inputs()
