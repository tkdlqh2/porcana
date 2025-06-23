from typing import List, Optional
from uuid import uuid4

from .schema import Card, CardCreate

_cards: List[Card] = []


def create_card(data: CardCreate) -> Card:
    """
    Create a new card with a unique identifier and add it to the in-memory collection.
    
    Parameters:
        data (CardCreate): The data used to create the new card.
    
    Returns:
        Card: The newly created card instance.
    """
    card = Card(id=uuid4(), **data.dict())
    _cards.append(card)
    return card


def list_cards() -> List[Card]:
    """
    Return the list of all stored card instances.
    
    Returns:
        List[Card]: The current collection of cards in memory.
    """
    return _cards


def get_card(card_id) -> Optional[Card]:
    """
    Retrieve a card from the in-memory collection by its unique identifier.
    
    Parameters:
    	card_id: The unique identifier of the card to retrieve.
    
    Returns:
    	The matching Card instance if found; otherwise, None.
    """
    for c in _cards:
        if str(c.id) == str(card_id):
            return c
    return None
