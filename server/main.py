import os
import psycopg

from fastapi import FastAPI, HTTPException

app = FastAPI(
    title="Clutch Alarm API",
    version="0.1.0"
)


@app.get("/")
def root():
    return {
        "message": "Clutch Alarm Server is running"
    }


@app.get("/health")
def health_check():
    return {
        "status": "ok",
        "service": "clutch-alarm"
    }


@app.get("/health/db")
def database_health():
    database_url = os.getenv("DATABASE_URL")

    if not database_url:
        raise HTTPException(
            status_code=503,
            detail="Database is not configured"
        )

    try:
        with psycopg.connect(
            database_url, connect_timeout=5
        ) as conn:
            with conn.cursor() as cursor:
                cursor.execute("SELECT 1")
                cursor.fetchone()

        return {"database": "connected"}

    except psycopg.Error:
        raise HTTPException(
            status_code=503,
            detail="Database connection failed"
        )