# Stage 1: Install the Python dependencies and download the model.
FROM python:3.11-slim-bookworm AS build
WORKDIR /build

RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
ENV HF_HOME=/opt/model-cache

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .
# Importing main loads the same model used by the application.
RUN python -c "import main"

# Stage 2: Run the service with the installed dependencies and model.
FROM python:3.11-slim-bookworm
WORKDIR /app

ENV PATH="/opt/venv/bin:$PATH"
ENV HF_HOME=/opt/model-cache
ENV HF_HUB_OFFLINE=1
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

COPY --from=build /opt/venv /opt/venv
COPY --from=build /build/main.py .

RUN groupadd --gid 10001 app
RUN useradd --uid 10001 --gid app --no-create-home app
COPY --from=build --chown=app:app /opt/model-cache /opt/model-cache
USER app

EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
