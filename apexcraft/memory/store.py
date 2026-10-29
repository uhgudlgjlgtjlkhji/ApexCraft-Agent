"""World-scoped, atomic JSON memory; observations override stale knowledge."""
from dataclasses import asdict
import json
import os
from pathlib import Path
import tempfile
from apexcraft.planning.goals import Goal


class WorldKnowledge:
    SCHEMA = 1

    def __init__(self, world_id):
        if not world_id:
            raise ValueError('World identity required')
        self.world_id = world_id
        self.resources = {}
        self.structures = {}
        self.hazards = {}
        self.failures = {}

    def observe(self, position, block, version):
        key = ','.join(map(str,position))
        record = {'block':block,'version':version}
        for collection in (self.resources,self.structures,self.hazards):
            collection.pop(key,None)
        if block is None:
            return
        collection = (self.hazards if block in {'lava','bedrock'} else self.structures
                      if block in {'crafting_table','furnace','bed','portal'} else self.resources)
        collection[key] = record

    def reject(self, strategy, signature, tick, *, ttl=200):
        if ttl < 1:
            raise ValueError('Failure TTL must be positive')
        self.failures[strategy] = {'signature':signature,'expires':tick+ttl}

    def rejected(self, strategy, signature, tick):
        record = self.failures.get(strategy)
        if not record:
            return False
        if record['signature'] != signature or record['expires'] <= tick:
            del self.failures[strategy]
            return False
        return True

    def save(self, path, manager):
        payload = {'schema':self.SCHEMA,'world_id':self.world_id,
                   'resources':self.resources,'structures':self.structures,
                   'hazards':self.hazards,'failures':self.failures,
                   'goal':asdict(manager.goal) if manager.goal else None,
                   'replans':manager.replans,'fingerprints':sorted(manager.failures),
                   'completed_steps':manager.completed_steps}
        path = Path(path)
        path.parent.mkdir(parents=True,exist_ok=True)
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(mode='w',dir=path.parent,delete=False) as handle:
                temporary = handle.name
                json.dump(payload,handle,sort_keys=True)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary,path)
        finally:
            if temporary and os.path.exists(temporary):
                os.unlink(temporary)

    def restore(self, path, manager):
        data = json.loads(Path(path).read_text())
        if data.get('schema') != self.SCHEMA or data.get('world_id') != self.world_id:
            raise ValueError('Checkpoint schema or world identity mismatch')
        for name in ('resources','structures','hazards','failures'):
            if not isinstance(data.get(name),dict):
                raise ValueError(f'Invalid checkpoint collection: {name}')
        goal_data = data.get('goal')
        goal = Goal(**goal_data) if goal_data else None
        if goal and goal.target is not None:
            goal = Goal(goal.description,goal.requirements,goal.equipment,tuple(goal.target),goal.blueprint)
        if data.get('replans',-1) < 0 or data.get('completed_steps',-1) < 0:
            raise ValueError('Invalid checkpoint counters')
        if goal:
            manager.submit(goal)
        for name in ('resources','structures','hazards','failures'):
            setattr(self,name,data[name])
        manager.replans = data['replans']
        manager.completed_steps = data['completed_steps']
        manager.failures = set(data.get('fingerprints',[]))
        # No cached action or inventory is replayed; next tick replans from observations.
