from fastapi import FastAPI
from pydantic import BaseModel
import base64

app = FastAPI(title="Porcana API")

class Deck(BaseModel):
    code: str

@app.get("/")
async def root():
    return {"message": "Porcana backend"}

@app.post("/decode")
async def decode_deck(deck: Deck):
    try:
        decoded = base64.b64decode(deck.code).decode('utf-8')
        return {"decoded": decoded}
    except Exception:
        return {"error": "Invalid deck code"}
