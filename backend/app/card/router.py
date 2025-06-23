from typing import List
from fastapi import APIRouter, HTTPException

from .domain import create_card, list_cards, get_card
from .schema import Card, CardCreate

router = APIRouter(prefix="/cards", tags=["cards"])


@router.post("/", response_model=Card)
async def create(card: CardCreate):
    """
    Create a new card using the provided card data.
    
    Parameters:
    	card (CardCreate): The data required to create a new card.
    
    Returns:
    	Card: The created card object.
    """
    return create_card(card)


@router.get("/", response_model=List[Card])
async def read_cards():
    """
    Retrieve a list of all cards.
    
    Returns:
        List[Card]: A list of card objects.
    """
    return list_cards()


@router.get("/{card_id}", response_model=Card)
async def read_card(card_id: str):
    """
    Retrieve a card by its unique identifier.
    
    Parameters:
        card_id (str): The unique identifier of the card to retrieve.
    
    Returns:
        Card: The card object corresponding to the given ID.
    
    Raises:
        HTTPException: If no card with the specified ID is found, raises a 404 error.
    """
    card = get_card(card_id)
    if not card:
        raise HTTPException(status_code=404, detail="Card not found")
    return card
