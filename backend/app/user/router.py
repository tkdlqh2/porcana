from typing import List
from fastapi import APIRouter, HTTPException

from .domain import create_user, list_users, get_user
from .schema import User, UserCreate

router = APIRouter(prefix="/users", tags=["users"])


@router.post("/", response_model=User)
async def create(user: UserCreate):
    """
    Create a new user with the provided information.
    
    Parameters:
        user (UserCreate): The data required to create a new user.
    
    Returns:
        User: The created user object.
    """
    return create_user(user)


@router.get("/", response_model=List[User])
async def read_users():
    """
    Retrieve a list of all users.
    
    Returns:
        List[User]: A list of user objects.
    """
    return list_users()


@router.get("/{user_id}", response_model=User)
async def read_user(user_id: str):
    """
    Retrieve a user by their unique identifier.
    
    Parameters:
        user_id (str): The unique identifier of the user to retrieve.
    
    Returns:
        User: The user object corresponding to the given ID.
    
    Raises:
        HTTPException: If no user with the specified ID is found, raises a 404 error.
    """
    user = get_user(user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user
