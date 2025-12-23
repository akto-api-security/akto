"""
Fine-tune ONNX models using client feedback from MongoDB.

This script:
1. Fetches training data from MongoDB (events marked as TRAINING status)
2. Fine-tunes the base model
3. Converts to ONNX format
4. Validates and saves the model
"""

import os
import json
import logging
from typing import List, Dict, Tuple
from datetime import datetime
import torch
import sys
from transformers import (
    AutoTokenizer,
    AutoModelForSequenceClassification,
    Trainer,
    TrainingArguments,
)
from optimum.onnxruntime import ORTModelForSequenceClassification
from optimum.onnxruntime.configuration import AutoQuantizationConfig
from optimum.onnxruntime import ORTQuantizer
from pymongo import MongoClient
from datasets import Dataset
from huggingface_hub import HfApi, login as hf_login
from transformers import AutoModelForSequenceClassification, AutoTokenizer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class ModelTrainer:
    """Train and convert models for agent guard service."""
    
    def __init__(
        self,
        model_name: str,
        mongo_uri: str,
        db_name: str,
        output_dir: str = "./fine_tuned_models"
    ):
        self.model_name = model_name
        self.mongo_uri = mongo_uri
        self.db_name = db_name
        self.output_dir = output_dir
        self.tokenizer = None
        self.model = None
        
    def fetch_training_data(self, account_id: str, category: str = None) -> List[Dict]:
        """
        Fetch training data from MongoDB.
        
        Args:
            account_id: Account ID (database name)
            category: Optional category filter (e.g., 'PROMPT_INJECTION')
        
        Returns:
            List of training examples with 'text' and 'label' fields
        """
        client = MongoClient(self.mongo_uri)
        db = client[account_id]
        collection = db["malicious_events"]
        
        # Build query for TRAINING status events
        query = {
            "status": "TRAINING",
            "label": "GUARDRAIL"
        }
        
        if category:
            query["subCategory"] = category
        
        logger.info(f"Fetching training data with query: {query}")
        
        events = list(collection.find(query))
        logger.info(f"Found {len(events)} training events")
        
        training_data = []
        for event in events:
            # Extract text from latestApiOrig (request payload)
            text = event.get("latestApiOrig", "")
            if not text:
                continue
            
            # Label: 1 for malicious (guardrail violation), 0 for false positive
            # Since these are marked for training, they're confirmed violations
            label = 1
            
            # If there's metadata indicating false positive, label as 0
            metadata = event.get("metadata", "")
            if isinstance(metadata, str) and "false_positive" in metadata.lower():
                label = 0
            
            training_data.append({
                "text": text,
                "label": label,
                "event_id": event.get("_id"),
                "category": event.get("subCategory", ""),
                "detected_at": event.get("detectedAt", 0)
            })
        
        client.close()
        logger.info(f"Prepared {len(training_data)} training examples")
        return training_data
    
    def prepare_dataset(self, training_data: List[Dict], test_split: float = 0.2) -> Tuple[Dataset, Dataset]:
        """
        Prepare HuggingFace dataset from training data.
        
        Args:
            training_data: List of training examples
            test_split: Fraction of data to use for testing
        
        Returns:
            Tuple of (train_dataset, test_dataset)
        """
        if len(training_data) < 10:
            raise ValueError(f"Insufficient training data: {len(training_data)} examples. Need at least 10.")
        
        # Split data
        split_idx = int(len(training_data) * (1 - test_split))
        train_data = training_data[:split_idx]
        test_data = training_data[split_idx:]
        
        logger.info(f"Train examples: {len(train_data)}, Test examples: {len(test_data)}")
        
        # Create datasets
        train_dataset = Dataset.from_list(train_data)
        test_dataset = Dataset.from_list(test_data)
        
        # Tokenize
        def tokenize(batch):
            return self.tokenizer(
                batch["text"],
                truncation=True,
                padding="max_length",
                max_length=512
            )
        
        train_dataset = train_dataset.map(tokenize, batched=True)
        test_dataset = test_dataset.map(tokenize, batched=True)
        
        # Rename label column
        train_dataset = train_dataset.rename_column("label", "labels")
        test_dataset = test_dataset.rename_column("label", "labels")
        
        # Set format
        train_dataset.set_format(type="torch", columns=["input_ids", "attention_mask", "labels"])
        test_dataset.set_format(type="torch", columns=["input_ids", "attention_mask", "labels"])
        
        return train_dataset, test_dataset
    
    def train(
        self,
        train_dataset: Dataset,
        test_dataset: Dataset,
        num_epochs: int = 3,
        learning_rate: float = 2e-5,
        batch_size: int = 8
    ):
        """
        Fine-tune the model.
        
        Args:
            train_dataset: Training dataset
            test_dataset: Test dataset
            num_epochs: Number of training epochs
            learning_rate: Learning rate
            batch_size: Batch size per device
        """
        logger.info(f"Starting training with {num_epochs} epochs, lr={learning_rate}, batch_size={batch_size}")
        
        training_args = TrainingArguments(
            output_dir=f"{self.output_dir}/checkpoints",
            evaluation_strategy="epoch",
            save_strategy="epoch",
            learning_rate=learning_rate,
            per_device_train_batch_size=batch_size,
            per_device_eval_batch_size=batch_size,
            num_train_epochs=num_epochs,
            weight_decay=0.01,
            logging_dir=f"{self.output_dir}/logs",
            logging_steps=50,
            load_best_model_at_end=True,
            metric_for_best_model="loss",
            greater_is_better=False,
            save_total_limit=2,
        )
        
        trainer = Trainer(
            model=self.model,
            args=training_args,
            train_dataset=train_dataset,
            eval_dataset=test_dataset,
            tokenizer=self.tokenizer,
        )
        
        trainer.train()
        trainer.save_model(f"{self.output_dir}/pytorch_model")
        self.tokenizer.save_pretrained(f"{self.output_dir}/pytorch_model")
        
        logger.info(f"✅ Fine-tuned PyTorch model saved at {self.output_dir}/pytorch_model")
    
    def convert_to_onnx(self, quantization: bool = True):
        """
        Convert fine-tuned PyTorch model to ONNX format.
        
        Args:
            quantization: Whether to apply quantization for faster inference
        """
        logger.info("Converting PyTorch model to ONNX...")
        
        # Load the fine-tuned model
        model_path = f"{self.output_dir}/pytorch_model"
        onnx_output_dir = f"{self.output_dir}/onnx_model"
        
        # Export to ONNX
        from optimum.onnxruntime import ORTModelForSequenceClassification
        from optimum.onnxruntime.configuration import AutoQuantizationConfig
        from optimum.onnxruntime import ORTQuantizer
        
        # Convert PyTorch model to ONNX
        ort_model = ORTModelForSequenceClassification.from_pretrained(
            model_path,
            export=True
        )
        ort_model.save_pretrained(onnx_output_dir)
        self.tokenizer.save_pretrained(onnx_output_dir)
        
        logger.info(f"✅ Base ONNX model saved at {onnx_output_dir}")
        
        # Apply quantization if requested
        if quantization:
            logger.info("Applying quantization...")
            quantizer = ORTQuantizer.from_pretrained(onnx_output_dir)
            qconfig = AutoQuantizationConfig.avx512_vnni(is_static=False, per_channel=False)
            
            quantizer.quantize(
                save_dir=f"{self.output_dir}/onnx_model_quantized",
                quantization_config=qconfig
            )
            logger.info(f"✅ Quantized ONNX model saved at {self.output_dir}/onnx_model_quantized")
            return f"{self.output_dir}/onnx_model_quantized"
        
        return onnx_output_dir
    
    def validate_model(self, onnx_model_path: str, test_texts: List[str]):
        """
        Validate the ONNX model with test examples.
        
        Args:
            onnx_model_path: Path to ONNX model
            test_texts: List of test texts
        """
        logger.info("Validating ONNX model...")
        
        from optimum.onnxruntime import ORTModelForSequenceClassification
        
        model = ORTModelForSequenceClassification.from_pretrained(onnx_model_path)
        tokenizer = AutoTokenizer.from_pretrained(onnx_model_path)
        
        for text in test_texts[:3]:  # Test first 3
            inputs = tokenizer(text, return_tensors="pt", truncation=True, max_length=512)
            outputs = model(**inputs)
            predictions = torch.nn.functional.softmax(outputs.logits, dim=-1)
            logger.info(f"Text: {text[:50]}... | Prediction: {predictions[0].tolist()}")
        
        logger.info("✅ Model validation complete")
    
    def run_training_pipeline(
        self,
        account_id: str,
        category: str = None,
        num_epochs: int = 3,
        learning_rate: float = 2e-5,
        batch_size: int = 8,
        quantization: bool = True
    ) -> str:
        """
        Complete training pipeline.
        
        Returns:
            Path to the final ONNX model
        """
        logger.info("=" * 60)
        logger.info("Starting Model Training Pipeline")
        logger.info("=" * 60)
        
        # 1. Load base model
        logger.info(f"Loading base model: {self.model_name}")
        self.tokenizer = AutoTokenizer.from_pretrained(self.model_name)
        self.model = AutoModelForSequenceClassification.from_pretrained(self.model_name)
        
        # 2. Fetch training data
        training_data = self.fetch_training_data(account_id, category)
        if len(training_data) < 10:
            raise ValueError(f"Insufficient training data: {len(training_data)} examples")
        
        # 3. Prepare dataset
        train_dataset, test_dataset = self.prepare_dataset(training_data)
        
        # 4. Train
        self.train(train_dataset, test_dataset, num_epochs, learning_rate, batch_size)
        
        # 5. Convert to ONNX
        onnx_model_path = self.convert_to_onnx(quantization)
        
        # 6. Validate
        test_texts = [ex["text"] for ex in training_data[:10]]
        self.validate_model(onnx_model_path, test_texts)
        
        logger.info("=" * 60)
        logger.info(f"✅ Training complete! Model saved at: {onnx_model_path}")
        logger.info("=" * 60)
        
        return onnx_model_path
    
    def push_to_huggingface(
        self,
        onnx_model_path: str,
        hf_model_name: str,
        hf_token: str = None,
        push_pytorch: bool = False
    ) -> str:
        """
        Push fine-tuned model to HuggingFace.
        
        Args:
            onnx_model_path: Path to ONNX model directory
            hf_model_name: HuggingFace model name (e.g., "TangoBeeAkto/deberta-prompt-injection-v2")
            hf_token: HuggingFace token (or use HUGGINGFACE_TOKEN env var)
            push_pytorch: Also push PyTorch model (for ONNX conversion on HF)
        
        Returns:
            HuggingFace model name
        """
        logger.info(f"Pushing model to HuggingFace: {hf_model_name}")
        
        # Login to HuggingFace
        if hf_token:
            hf_login(token=hf_token)
        elif os.getenv("HUGGINGFACE_TOKEN"):
            hf_login(token=os.getenv("HUGGINGFACE_TOKEN"))
        else:
            raise ValueError("HuggingFace token required. Set HUGGINGFACE_TOKEN env var or pass hf_token")
        
        api = HfApi()
        
        # Push ONNX model
        logger.info(f"Uploading ONNX model from {onnx_model_path}...")
        api.upload_folder(
            folder_path=onnx_model_path,
            repo_id=hf_model_name,
            repo_type="model",
            commit_message=f"Fine-tuned model trained on {datetime.now().isoformat()}"
        )
        
        # Optionally push PyTorch model for ONNX conversion on HF
        if push_pytorch:
            pytorch_path = f"{self.output_dir}/pytorch_model"
            if os.path.exists(pytorch_path):
                logger.info("Uploading PyTorch model...")
                api.upload_folder(
                    folder_path=pytorch_path,
                    repo_id=hf_model_name,
                    repo_type="model",
                    commit_message="PyTorch model for ONNX conversion"
                )
        
        logger.info(f"✅ Model pushed to HuggingFace: https://huggingface.co/{hf_model_name}")
        return hf_model_name


def main():
    """Main entry point."""
    import argparse
    
    parser = argparse.ArgumentParser(description="Fine-tune ONNX models for agent guard")
    parser.add_argument("--model-name", type=str, 
                       default="protectai/deberta-v3-base-prompt-injection-v2",
                       help="Base model name")
    parser.add_argument("--mongo-uri", type=str, required=True,
                       help="MongoDB connection URI")
    parser.add_argument("--account-id", type=str, required=True,
                       help="Account ID (database name)")
    parser.add_argument("--category", type=str, default=None,
                       help="Category filter (e.g., PROMPT_INJECTION)")
    parser.add_argument("--output-dir", type=str, default="./fine_tuned_models",
                       help="Output directory for models")
    parser.add_argument("--epochs", type=int, default=3,
                       help="Number of training epochs")
    parser.add_argument("--learning-rate", type=float, default=2e-5,
                       help="Learning rate")
    parser.add_argument("--batch-size", type=int, default=8,
                       help="Batch size")
    parser.add_argument("--no-quantization", action="store_true",
                       help="Disable quantization")
    
    args = parser.parse_args()
    
    trainer = ModelTrainer(
        model_name=args.model_name,
        mongo_uri=args.mongo_uri,
        db_name=args.account_id,
        output_dir=args.output_dir
    )
    
    try:
        model_path = trainer.run_training_pipeline(
            account_id=args.account_id,
            category=args.category,
            num_epochs=args.epochs,
            learning_rate=args.learning_rate,
            batch_size=args.batch_size,
            quantization=not args.no_quantization
        )
        print(f"\n✅ Success! Model ready at: {model_path}")
    except Exception as e:
        logger.error(f"Training failed: {e}", exc_info=True)
        raise


if __name__ == "__main__":
    main()

