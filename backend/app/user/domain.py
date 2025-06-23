from typing import List, Optional
from uuid import uuid4

from .schema import User, UserCreate

_users: List[User] = []


def create_user(data: UserCreate) -> User:
    """
    Create a new user with a unique ID and add it to the in-memory user list.
    
    Parameters:
        data (UserCreate): The data required to create a new user.
    
    Returns:
        User: The newly created user instance.
    """
    user = User(id=uuid4(), **data.dict())
    _users.append(user)
    return user


def list_users() -> List[User]:
    """
    Return the list of all stored users.
    
    Returns:
        List[User]: All user instances currently in memory.
    """
    return _users


def get_user(user_id) -> Optional[User]:
    """
    Retrieve a user by their unique identifier.
    
    Parameters:
        user_id: The unique identifier of the user to retrieve.
    
    Returns:
        The User instance with the matching ID, or None if no such user exists.
    """
    for u in _users:
        if str(u.id) == str(user_id):
            return u
    return None
