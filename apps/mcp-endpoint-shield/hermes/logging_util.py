"""
Logging utilities for Hermes Akto Guardrails plugin.
Sets up file and console logging with appropriate levels.
"""

import os
import logging
import sys
from typing import Optional


def setup_logger(
    name: str,
    level: str = "INFO",
    log_dir: Optional[str] = None
) -> logging.Logger:
    """
    Setup logger with file and console handlers.

    Args:
        name: Logger name
        level: Log level (DEBUG, INFO, WARNING, ERROR)
        log_dir: Directory for log files (default: ~/.config/hermes/akto/logs)

    Returns:
        Configured logger instance
    """
    if log_dir is None:
        log_dir = os.path.expanduser("~/.config/hermes/akto/logs")

    # Create log directory
    os.makedirs(log_dir, exist_ok=True)

    # Create logger
    logger = logging.getLogger(name)
    logger.setLevel(getattr(logging, level, logging.INFO))

    # Remove existing handlers to avoid duplicates
    logger.handlers = []

    # Format
    formatter = logging.Formatter(
        "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    )

    # File handler
    log_file = os.path.join(log_dir, "hermes-guardrails.log")
    file_handler = logging.FileHandler(log_file)
    file_handler.setLevel(getattr(logging, level, logging.INFO))
    file_handler.setFormatter(formatter)
    logger.addHandler(file_handler)

    # Console handler (errors only)
    console_handler = logging.StreamHandler(sys.stderr)
    console_handler.setLevel(logging.ERROR)
    console_handler.setFormatter(formatter)
    logger.addHandler(console_handler)

    return logger
