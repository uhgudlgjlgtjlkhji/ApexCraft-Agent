"""Reproducible, fully observed finite resource fixtures."""
from apexcraft.simulation.world import SimulationWorld


def progression_world():
    game = SimulationWorld(size=24)
    # Known flat test terrain; does not simulate discovering caves or chunks.
    for item,z,count in [('log',2,20),('cobblestone',4,20),('raw_iron',6,32),
                         ('coal',8,32),('diamond',10,8),('food',12,8),('dirt',14,16)]:
        for index in range(count):
            game.blocks[(-16 + index%32,0,z+index//32)] = item
    return game
