from typing import List
from fastapi import APIRouter, HTTPException

from .domain import create_user, list_users, get_user
from .schema import User, UserCreate

router = APIRouter(prefix="/users", tags=["users"])


@router.post("/", response_model=User)
async def create(user: UserCreate):
    return create_user(user)


@router.get("/", response_model=List[User])
async def read_users():
    return list_users()


@router.get("/{user_id}", response_model=User)
async def read_user(user_id: str):
    user = get_user(user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user
