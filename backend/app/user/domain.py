from typing import List, Optional
from uuid import uuid4

from .schema import User, UserCreate

_users: List[User] = []


def create_user(data: UserCreate) -> User:
    user = User(id=uuid4(), **data.dict())
    _users.append(user)
    return user


def list_users() -> List[User]:
    return _users


def get_user(user_id) -> Optional[User]:
    for u in _users:
        if str(u.id) == str(user_id):
            return u
    return None
