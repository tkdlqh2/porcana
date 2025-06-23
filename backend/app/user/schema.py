from pydantic import BaseModel
from uuid import UUID

class User(BaseModel):
    id: UUID
    username: str
    email: str

class UserCreate(BaseModel):
    username: str
    email: str
