from typing import List, Optional
from uuid import uuid4
import base64

from .schema import Deck, DeckCreate

_decks: List[Deck] = []


def create_deck(data: DeckCreate) -> Deck:
    """
    Create a new deck with a unique identifier and add it to the collection.
    
    Parameters:
        data (DeckCreate): The data used to initialize the new deck.
    
    Returns:
        Deck: The newly created deck instance.
    """
    deck = Deck(id=uuid4(), **data.dict())
    _decks.append(deck)
    return deck


def list_decks() -> List[Deck]:
    """
    Return the list of all stored Deck objects.
    """
    return _decks


def get_deck(deck_id) -> Optional[Deck]:
    """
    Retrieve a deck from the collection by its unique identifier.
    
    Parameters:
    	deck_id: The unique identifier of the deck to retrieve.
    
    Returns:
    	The matching Deck instance if found, otherwise None.
    """
    for d in _decks:
        if str(d.id) == str(deck_id):
            return d
    return None


def decode_deck(code: str) -> Optional[str]:
    """
    Decode a base64-encoded string into a UTF-8 string.
    
    Parameters:
        code (str): The base64-encoded string to decode.
    
    Returns:
        str or None: The decoded UTF-8 string if decoding succeeds; otherwise, None if decoding fails.
    """
    try:
        return base64.b64decode(code).decode("utf-8")
    except Exception:
        return None
