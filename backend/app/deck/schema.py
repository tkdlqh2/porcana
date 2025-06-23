from pydantic import BaseModel
from uuid import UUID

class Deck(BaseModel):
    id: UUID
    title: str
    description: str
    user_id: UUID

class DeckCreate(BaseModel):
    title: str
    description: str
    user_id: UUID

class DeckCode(BaseModel):
    code: str
