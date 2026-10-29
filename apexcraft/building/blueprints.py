"""Declarative shelter geometry; every placed block is a final postcondition."""


def starter_shelter(origin=(2,0,0)):
    ox,oy,oz = origin
    blocks = {}
    for x in range(5):
        for z in range(5):
            blocks[(ox+x,oy-1,oz+z)] = 'planks'
            blocks[(ox+x,oy+2,oz+z)] = 'planks'
            if x in {0,4} or z in {0,4}:
                for y in range(2):
                    blocks[(ox+x,oy+y,oz+z)] = 'planks'
    blocks[(ox,oy,oz+2)] = 'door'
    blocks.pop((ox,oy+1,oz+2))
    blocks[(ox+2,oy,oz+2)] = 'torch'
    return tuple(blocks.items())
