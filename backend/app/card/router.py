from typing import List
from fastapi import APIRouter, HTTPException

from .domain import create_card, list_cards, get_card
from .schema import Card, CardCreate

router = APIRouter(prefix="/cards", tags=["cards"])


@router.post("/", response_model=Card)
async def create(card: CardCreate):
    return create_card(card)


@router.get("/", response_model=List[Card])
async def read_cards():
    return list_cards()


@router.get("/{card_id}", response_model=Card)
async def read_card(card_id: str):
    card = get_card(card_id)
    if not card:
        raise HTTPException(status_code=404, detail="Card not found")
    return card
