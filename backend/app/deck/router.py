from typing import List
from fastapi import APIRouter, HTTPException

from .domain import create_deck, list_decks, get_deck, decode_deck
from .schema import Deck, DeckCreate, DeckCode

router = APIRouter(prefix="/decks", tags=["decks"])


@router.post("/", response_model=Deck)
async def create(deck: DeckCreate):
    return create_deck(deck)


@router.get("/", response_model=List[Deck])
async def read_decks():
    return list_decks()


@router.get("/{deck_id}", response_model=Deck)
async def read_deck(deck_id: str):
    deck = get_deck(deck_id)
    if not deck:
        raise HTTPException(status_code=404, detail="Deck not found")
    return deck


@router.post("/decode")
async def decode(payload: DeckCode):
    result = decode_deck(payload.code)
    if result is None:
        raise HTTPException(status_code=400, detail="Invalid deck code")
    return {"decoded": result}
