from typing import List, Optional
from uuid import uuid4
import base64

from .schema import Deck, DeckCreate

_decks: List[Deck] = []


def create_deck(data: DeckCreate) -> Deck:
    deck = Deck(id=uuid4(), **data.dict())
    _decks.append(deck)
    return deck


def list_decks() -> List[Deck]:
    return _decks


def get_deck(deck_id) -> Optional[Deck]:
    for d in _decks:
        if str(d.id) == str(deck_id):
            return d
    return None


def decode_deck(code: str) -> Optional[str]:
    try:
        return base64.b64decode(code).decode("utf-8")
    except Exception:
        return None
