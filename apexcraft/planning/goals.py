from dataclasses import dataclass, field
import re


ARMOUR = {'head':'iron_helmet','chest':'iron_chestplate',
          'legs':'iron_leggings','feet':'iron_boots'}


@dataclass(frozen=True)
class Goal:
    description: str
    requirements: dict[str,int] = field(default_factory=dict)
    equipment: dict[str,str] = field(default_factory=dict)
    target: tuple[int,int,int] | None = None

    def verify(self, snapshot, driver):
        return (all(snapshot.inventory.get(item,0) >= n for item,n in self.requirements.items())
                and all(driver.equipment.get(slot) == item for slot,item in self.equipment.items())
                and (self.target is None or tuple(snapshot.position) == self.target))


def parse_goal(text):
    normalized = text.lower().strip().rstrip('.')
    if normalized.startswith('start from nothing and '):
        normalized = normalized[len('start from nothing and '):]
    if re.fullmatch(r'(get|obtain) full iron armou?r',normalized):
        return Goal(text,equipment=dict(ARMOUR))
    match = re.fullmatch(r'(?:get|obtain|collect|craft) (?:(\d+) )?([a-z_ ]+)',normalized)
    if match:
        amount = int(match[1] or 1)
        if not 1 <= amount <= 100000:
            raise ValueError('Quantity must be between 1 and 100000')
        return Goal(text,{match[2].replace(' ','_'):amount})
    raise ValueError('Supported goals: collect/craft/get <amount> <item>, get full iron armour')
