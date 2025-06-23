from pydantic import BaseModel
from uuid import UUID

class Card(BaseModel):
    id: UUID
    ticker: str
    company_name: str
    sector: str
    weight_percentage: float
    deck_id: UUID

class CardCreate(BaseModel):
    ticker: str
    company_name: str
    sector: str
    weight_percentage: float
    deck_id: UUID
