# Porcana

This repository contains a minimal skeleton for the Porcana project.

## Stack

- **Backend**: Python FastAPI with LangChain
- **Frontend**: React with React Router v5 and Zustand
- **Infrastructure**: Docker Compose

## Development

1. Build and start services:

```bash
docker-compose up --build
```

2. Access the API at `http://localhost:8000`.
3. Access the frontend at `http://localhost:3000`.

## License

See [LICENSE](LICENSE).

## API Overview

The backend organizes functionality by domain under `backend/app/`. Each domain exposes a router mounted in `main.py`.

- `GET /users` - list users
- `GET /decks` - list decks
- `POST /decks/decode` - decode a deck code
- `GET /cards` - list cards

These routes currently store data in memory for demonstration purposes.
