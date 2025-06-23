from fastapi import FastAPI

from .user.router import router as user_router
from .deck.router import router as deck_router
from .card.router import router as card_router

app = FastAPI(title="Porcana API")

app.include_router(user_router)
app.include_router(deck_router)
app.include_router(card_router)


@app.get("/")
async def root():
    """
    Handle the root endpoint and return a welcome message for the Porcana backend.
    """
    return {"message": "Porcana backend"}
