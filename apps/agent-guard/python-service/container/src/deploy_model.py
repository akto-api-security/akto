"""
Deploy fine-tuned model to production.

Usage:
    python deploy_model.py \
        --training-output ./fine_tuned_models/job-123/onnx_model_quantized \
        --scanner-name PromptInjection \
        --version v2
"""

import os
import sys
import shutil
import argparse
from pathlib import Path

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from model_registry import model_registry


def deploy_model(
    training_output_path: str,
    scanner_name: str = "PromptInjection",
    version: str = "latest",
    models_base_dir: str = None
):
    """
    Deploy a trained model to production.
    
    Args:
        training_output_path: Path to trained ONNX model directory
        scanner_name: Scanner name (e.g., "PromptInjection")
        version: Version identifier (e.g., "v2", "2024-01-15")
        models_base_dir: Base directory for models (default: /app/models)
    """
    if not os.path.exists(training_output_path):
        raise ValueError(f"Training output path does not exist: {training_output_path}")
    
    # Determine deployment path
    if models_base_dir is None:
        models_base_dir = os.getenv("MODELS_BASE_DIR", "/app/models")
    
    deployed_path = os.path.join(
        models_base_dir,
        scanner_name.lower(),
        version,
        "onnx_model_quantized"
    )
    
    # Create directory structure
    os.makedirs(os.path.dirname(deployed_path), exist_ok=True)
    
    # Copy model files
    print(f"Copying model from {training_output_path} to {deployed_path}...")
    if os.path.exists(deployed_path):
        shutil.rmtree(deployed_path)
    shutil.copytree(training_output_path, deployed_path)
    
    # Register model
    print(f"Registering model in registry...")
    model_registry.register_model(
        scanner_name=scanner_name,
        model_path=deployed_path,
        version=version,
        metadata={
            "deployed_at": str(Path(deployed_path).stat().st_mtime),
            "source": training_output_path
        }
    )
    
    print(f"✅ Model deployed successfully!")
    print(f"   Scanner: {scanner_name}")
    print(f"   Version: {version}")
    print(f"   Path: {deployed_path}")
    print(f"\n⚠️  Note: Clear scanner cache to use new model:")
    print(f"   POST http://localhost:8092/scanners/cache/clear?scanner_name={scanner_name}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Deploy fine-tuned model")
    parser.add_argument("--training-output", type=str, required=True,
                       help="Path to training output (ONNX model directory)")
    parser.add_argument("--scanner-name", type=str, default="PromptInjection",
                       help="Scanner name")
    parser.add_argument("--version", type=str, default="latest",
                       help="Model version")
    parser.add_argument("--models-base-dir", type=str, default=None,
                       help="Base directory for models")
    
    args = parser.parse_args()
    
    try:
        deploy_model(
            training_output_path=args.training_output,
            scanner_name=args.scanner_name,
            version=args.version,
            models_base_dir=args.models_base_dir
        )
    except Exception as e:
        print(f"❌ Deployment failed: {e}")
        sys.exit(1)

