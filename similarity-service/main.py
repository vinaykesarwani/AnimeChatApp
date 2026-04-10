from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer, util
import torch

app = FastAPI(title="Similarity Service")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Loaded once at startup — stays in memory for all requests
model = SentenceTransformer("all-MiniLM-L6-v2")


class SimilarityRequest(BaseModel):
    candidate: str          # the title the user is typing
    existingTitles: list[str]  # all room titles for that anime


class SimilarityResult(BaseModel):
    title: str
    score: float            # cosine similarity 0.0 – 1.0


class SimilarityResponse(BaseModel):
    results: list[SimilarityResult]


@app.post("/similarity", response_model=SimilarityResponse)
def check_similarity(req: SimilarityRequest):
    if not req.existingTitles:
        return SimilarityResponse(results=[])

    # Encode candidate + all existing titles in one batch
    all_texts = [req.candidate] + req.existingTitles
    embeddings = model.encode(all_texts, convert_to_tensor=True)

    candidate_emb = embeddings[0]
    existing_embs = embeddings[1:]

    # Cosine similarity between candidate and each existing title
    scores = util.cos_sim(candidate_emb, existing_embs)[0]

    results = [
        SimilarityResult(title=title, score=round(float(score), 4))
        for title, score in zip(req.existingTitles, scores)
    ]

    # Return sorted highest-first
    results.sort(key=lambda r: r.score, reverse=True)
    return SimilarityResponse(results=results)


@app.get("/health")
def health():
    return {"status": "ok"}
