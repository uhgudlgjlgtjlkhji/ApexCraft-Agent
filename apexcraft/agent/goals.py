"""One observed, verified step at a time; plans are regenerated after each step."""
from dataclasses import dataclass
from collections import Counter
from math import dist
from hashlib import sha256
from apexcraft.actions.acquisition import CollectResourceAction
from apexcraft.actions.interaction import InteractionAction
from apexcraft.agent.task import Task, TaskStatus, TERMINAL
from apexcraft.agent.scheduler import TaskScheduler
from apexcraft.agent.controller import AgentController
from apexcraft.inventory.recipes import DependencyResolver, standard_catalog
from apexcraft.navigation.movement import MoveAction
from apexcraft.planning.goals import parse_goal
from apexcraft.survival.manager import SurvivalManager
from apexcraft.memory.store import WorldKnowledge


@dataclass(frozen=True)
class GoalChanged:
    description: str
    status: str
    reason: str | None = None


class GoalManager:
    def __init__(self, driver, world, events, *, max_replans=8):
        self.driver, self.world, self.events = driver, world, events
        self.recipes, sources = standard_catalog()
        self.resolver = DependencyResolver(self.recipes,sources)
        self.scheduler = TaskScheduler()
        self.controller = AgentController(world,self.scheduler,driver.release_inputs)
        self.goal = self.current = None
        self.status = 'IDLE'
        self.reason = None
        self.max_replans = max_replans
        self.replans = self.serial = 0
        self.failures = set()
        self.completed_steps = 0
        self.survival = SurvivalManager(driver)
        self.safety_task = None
        self.memory = WorldKnowledge(driver.world_id)

    def checkpoint(self, path):
        self.memory.save(path,self)

    def restore(self, path):
        self.memory.restore(path,self)

    def _status(self, status, reason=None):
        self.status, self.reason = status, reason
        self.events.publish(GoalChanged(self.goal.description if self.goal else '',status,reason))

    def submit(self, goal):
        parsed = parse_goal(goal) if isinstance(goal,str) else goal
        self.controller.stop()
        self.scheduler.tasks.clear()
        self.goal, self.current = parsed, None
        self.safety_task = None
        self.replans = self.completed_steps = 0
        self.failures.clear()
        self.controller.resume()
        self._status('RUNNING')

    def pause(self):
        self.controller.pause()
        self._status('PAUSED')

    def resume(self):
        if self.status != 'PAUSED':
            raise RuntimeError('Goal is not paused')
        self.controller.resume()
        self._status('RUNNING')

    def stop(self):
        try:
            self.controller.stop()
        finally:
            self._status('CANCELLED')

    def _next_action(self, snapshot):
        for slot,item in self.goal.equipment.items():
            if self.driver.equipment.get(slot) != item and snapshot.inventory.get(item,0):
                return InteractionAction(lambda: self.driver.equip(item,slot),
                    lambda w: self.driver.equipment.get(slot) == item,self.driver.release_inputs)
        requirements = dict(self.goal.requirements)
        for slot,item in self.goal.equipment.items():
            if self.driver.equipment.get(slot) != item:
                requirements[item] = max(requirements.get(item,0),1)
        missing_blocks = [(tuple(p),item) for p,item in self.goal.blueprint
                          if self.driver.blocks.get(tuple(p)) != item]
        if missing_blocks:
            for item,count in Counter(item for _,item in missing_blocks).items():
                requirements[item] = requirements.get(item,0)+count
        steps = self.resolver.resolve(requirements,snapshot.inventory)
        if steps:
            step = steps[0]
            if step.kind == 'collect':
                return CollectResourceAction(self.driver,step.item,
                    snapshot.inventory.get(step.item,0)+step.count)
            recipe = self.recipes[step.item]
            target = snapshot.inventory.get(step.item,0) + step.count*recipe.output
            return InteractionAction(lambda: self.driver.produce(recipe,step.count),
                lambda w: w.inventory.get(step.item,0) >= target,self.driver.release_inputs)
        if missing_blocks:
            reachable = [(p,item) for p,item in missing_blocks if self.driver.reachable(p)
                         and p != tuple(snapshot.position)]
            if reachable:
                position,item = reachable[0]
                if position in self.driver.blocks:
                    raise RuntimeError(f'Blueprint obstructed at {position}; refusing to destroy unknown work')
                return InteractionAction(lambda: self.driver.place(position,item),
                    lambda w: self.driver.blocks.get(position) == item,self.driver.release_inputs)
            position,_ = min(missing_blocks,key=lambda pair:dist(snapshot.position,pair[0]))
            approaches = [(x,0,z) for x in range(position[0]-3,position[0]+4)
                          for z in range(position[2]-3,position[2]+4)
                          if self.driver.traversable((x,0,z)) and dist((x,0,z),position) <= 4.5
                          and (x,0,z) not in {p for p,_ in missing_blocks}]
            if not approaches:
                raise RuntimeError('No safe blueprint approach')
            return MoveAction(self.driver,min(approaches,key=lambda p:dist(snapshot.position,p)))
        if self.goal.target is not None:
            return MoveAction(self.driver,self.goal.target)
        raise RuntimeError('No remaining steps but goal postcondition is false')

    def tick(self):
        if self.status != 'RUNNING':
            return
        snapshot = self.world.snapshot
        # The simulation supplies its fully observed finite map. A live adapter must
        # provide targeted observations instead of treating unknown chunks as air.
        known = set(self.memory.resources) | set(self.memory.structures) | set(self.memory.hazards)
        for key in known:
            position = tuple(map(int,key.split(',')))
            if position not in self.driver.blocks:
                self.memory.observe(position,None,snapshot.version)
        for position,block in self.driver.blocks.items():
            key = ','.join(map(str,position))
            old = (self.memory.resources.get(key) or self.memory.structures.get(key)
                   or self.memory.hazards.get(key))
            if not old or old['block'] != block:
                self.memory.observe(position,block,snapshot.version)
        if self.safety_task and self.safety_task.status in TERMINAL | {TaskStatus.BLOCKED}:
            if self.safety_task.status != TaskStatus.SUCCESS:
                self.controller.stop()
                self._status('BLOCKED',f'Survival failed: {self.safety_task.failure_reason}')
                return
            self.scheduler.tasks.remove(self.safety_task)
            self.safety_task = None
        if self.safety_task is None:
            try:
                safety = self.survival.action(snapshot)
            except RuntimeError as error:
                self.controller.stop()
                self._status('BLOCKED',str(error))
                return
            if safety:
                action, priority = safety
                self.serial += 1
                self.safety_task = Task(f'survival-{self.serial}',action,self.events,
                                        priority=priority,max_ticks=1000)
                self.scheduler.submit(self.safety_task)
        if self.safety_task:
            self.controller.tick()
            return
        if self.goal.verify(snapshot,self.driver):
            self.controller.stop()
            self._status('SUCCESS')
            return
        if self.current and self.current.status in TERMINAL | {TaskStatus.BLOCKED}:
            if self.current.status != TaskStatus.SUCCESS:
                fingerprint = sha256(repr((self.current.failure_reason,
                    sorted(snapshot.inventory.items()),sorted(self.driver.blocks.items()),
                    sorted(self.driver.durability.items()))).encode()).hexdigest()
                if fingerprint in self.failures or self.replans >= self.max_replans:
                    self.controller.stop()
                    self._status('BLOCKED',self.current.failure_reason)
                    return
                self.failures.add(fingerprint)
                self.replans += 1
            else:
                self.completed_steps += 1
            self.current.cancel()
            self.current = None
            # Bounded retained task history; decision events remain externally loggable.
            self.scheduler.tasks.clear()
        if self.current is None:
            try:
                action = self._next_action(snapshot)
            except (ValueError,RuntimeError) as error:
                self.controller.stop()
                self._status('BLOCKED',str(error))
                return
            self.serial += 1
            self.current = Task(f'step-{self.serial}',action,self.events,max_ticks=10000)
            self.scheduler.submit(self.current)
        self.controller.tick()
