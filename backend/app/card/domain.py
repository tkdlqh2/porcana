from typing import List, Optional
from uuid import uuid4

from .schema import Card, CardCreate

_cards: List[Card] = []


def create_card(data: CardCreate) -> Card:
    card = Card(id=uuid4(), **data.dict())
    _cards.append(card)
    return card


def list_cards() -> List[Card]:
    return _cards


def get_card(card_id) -> Optional[Card]:
    for c in _cards:
        if str(c.id) == str(card_id):
            return c
    return None
