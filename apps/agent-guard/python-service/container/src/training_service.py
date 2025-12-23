"""
Training service API for fine-tuning models.
"""

import os
import logging
import subprocess
from typing import Optional, Dict
from datetime import datetime
from fastapi import FastAPI, HTTPException, BackgroundTasks
from pydantic import BaseModel
import threading

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Model Training Service", version="1.0.0")

# Track training jobs
training_jobs: Dict[str, Dict] = {}


class TrainingRequest(BaseModel):
    account_id: str
    category: Optional[str] = None
    model_name: Optional[str] = "protectai/deberta-v3-base-prompt-injection-v2"
    epochs: int = 3
    learning_rate: float = 2e-5
    batch_size: int = 8
    quantization: bool = True


class TrainingResponse(BaseModel):
    job_id: str
    status: str
    message: str


def run_training_job(job_id: str, request: TrainingRequest, mongo_uri: str):
    """Run training in background."""
    try:
        training_jobs[job_id]["status"] = "running"
        training_jobs[job_id]["message"] = "Training started..."
        
        # Build command
        cmd = [
            "python", "-m", "model_training.train_fine_tuned_model",
            "--mongo-uri", mongo_uri,
            "--account-id", request.account_id,
            "--model-name", request.model_name,
            "--epochs", str(request.epochs),
            "--learning-rate", str(request.learning_rate),
            "--batch-size", str(request.batch_size),
            "--output-dir", f"./models/{job_id}"
        ]
        
        if request.category:
            cmd.extend(["--category", request.category])
        
        if not request.quantization:
            cmd.append("--no-quantization")
        
        logger.info(f"Running training command: {' '.join(cmd)}")
        
        # Run training
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            cwd="/app"
        )
        
        if result.returncode == 0:
            training_jobs[job_id]["status"] = "completed"
            training_jobs[job_id]["message"] = "Training completed successfully"
            training_jobs[job_id]["output"] = result.stdout
            training_jobs[job_id]["model_path"] = f"./models/{job_id}/onnx_model_quantized"
        else:
            training_jobs[job_id]["status"] = "failed"
            training_jobs[job_id]["message"] = f"Training failed: {result.stderr}"
            training_jobs[job_id]["error"] = result.stderr
            
    except Exception as e:
        training_jobs[job_id]["status"] = "failed"
        training_jobs[job_id]["message"] = f"Error: {str(e)}"
        training_jobs[job_id]["error"] = str(e)
        logger.error(f"Training job {job_id} failed: {e}", exc_info=True)


@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "model-training"}


@app.post("/train", response_model=TrainingResponse)
async def start_training(request: TrainingRequest, background_tasks: BackgroundTasks):
    """Start a training job."""
    import uuid
    job_id = str(uuid.uuid4())
    
    mongo_uri = os.getenv("MONGODB_URI", "mongodb://localhost:27017")
    
    # Initialize job tracking
    training_jobs[job_id] = {
        "status": "queued",
        "message": "Job queued",
        "request": request.dict(),
        "created_at": str(datetime.now())
    }
    
    # Start training in background
    thread = threading.Thread(
        target=run_training_job,
        args=(job_id, request, mongo_uri)
    )
    thread.daemon = True
    thread.start()
    
    return TrainingResponse(
        job_id=job_id,
        status="queued",
        message=f"Training job {job_id} started"
    )


@app.get("/train/{job_id}")
async def get_training_status(job_id: str):
    """Get training job status."""
    if job_id not in training_jobs:
        raise HTTPException(status_code=404, detail="Job not found")
    
    return training_jobs[job_id]


@app.get("/train")
async def list_training_jobs():
    """List all training jobs."""
    return {
        "jobs": list(training_jobs.keys()),
        "total": len(training_jobs)
    }


if __name__ == "__main__":
    import uvicorn
    from datetime import datetime
    port = int(os.getenv("TRAINING_PORT", 8094))
    uvicorn.run(app, host="0.0.0.0", port=port)

