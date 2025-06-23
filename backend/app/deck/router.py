from typing import List
from fastapi import APIRouter, HTTPException

from .domain import create_deck, list_decks, get_deck, decode_deck
from .schema import Deck, DeckCreate, DeckCode

router = APIRouter(prefix="/decks", tags=["decks"])


@router.post("/", response_model=Deck)
async def create(deck: DeckCreate):
    """
    Create a new deck using the provided deck data.
    
    Parameters:
        deck (DeckCreate): The data required to create a new deck.
    
    Returns:
        Deck: The created deck object.
    """
    return create_deck(deck)


@router.get("/", response_model=List[Deck])
async def read_decks():
    """
    Retrieve a list of all decks.
    
    Returns:
        List of deck objects.
    """
    return list_decks()


@router.get("/{deck_id}", response_model=Deck)
async def read_deck(deck_id: str):
    """
    Retrieve a deck by its unique identifier.
    
    Parameters:
        deck_id (str): The unique identifier of the deck to retrieve.
    
    Returns:
        Deck: The deck object corresponding to the provided ID.
    
    Raises:
        HTTPException: If no deck with the given ID is found, raises a 404 error.
    """
    deck = get_deck(deck_id)
    if not deck:
        raise HTTPException(status_code=404, detail="Deck not found")
    return deck


@router.post("/decode")
async def decode(payload: DeckCode):
    """
    Decode a deck code and return the decoded result.
    
    Raises an HTTP 400 error if the provided deck code is invalid.
    
    Parameters:
        payload (DeckCode): The payload containing the deck code to decode.
    
    Returns:
        dict: A dictionary with the decoded result under the key "decoded".
    """
    result = decode_deck(payload.code)
    if result is None:
        raise HTTPException(status_code=400, detail="Invalid deck code")
    return {"decoded": result}
