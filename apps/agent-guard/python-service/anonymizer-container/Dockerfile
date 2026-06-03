FROM python:3.12-slim

WORKDIR /app

COPY src/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
RUN python -m spacy download en_core_web_sm

COPY src/ .

ENV PYTHONUNBUFFERED=1
ENV PORT=8093
EXPOSE 8093

CMD ["uvicorn", "anonymizer_service:app", "--host", "0.0.0.0", "--port", "8093"]
