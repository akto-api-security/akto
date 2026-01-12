"""
Health Check Module
Production-ready health checks for monitoring.
"""

import logging
from typing import Dict, Any, Optional
from datetime import datetime
from src.core.stores import RAGStore, VariableStore, PatternLearner

logger = logging.getLogger(__name__)


class HealthChecker:
    """Health check manager."""
    
    def __init__(
        self,
        rag_store: Optional[RAGStore] = None,
        variable_store: Optional[VariableStore] = None,
        pattern_learner: Optional[PatternLearner] = None
    ):
        self.rag_store = rag_store
        self.variable_store = variable_store
        self.pattern_learner = pattern_learner
        self.start_time = datetime.utcnow()
    
    def check_health(self) -> Dict[str, Any]:
        """
        Perform comprehensive health check.
        
        Returns:
            Health status dictionary
        """
        health_status = {
            "status": "healthy",
            "timestamp": datetime.utcnow().isoformat(),
            "uptime_seconds": (datetime.utcnow() - self.start_time).total_seconds(),
            "components": {}
        }
        
        # Check RAG Store
        rag_status = self._check_rag_store()
        health_status["components"]["rag_store"] = rag_status
        
        # Check Variable Store
        var_status = self._check_variable_store()
        health_status["components"]["variable_store"] = var_status
        
        # Check Pattern Learner
        pattern_status = self._check_pattern_learner()
        health_status["components"]["pattern_learner"] = pattern_status
        
        # Overall status
        all_healthy = all(
            comp.get("status") == "healthy"
            for comp in health_status["components"].values()
        )
        
        if not all_healthy:
            health_status["status"] = "degraded"
        
        return health_status
    
    def _check_rag_store(self) -> Dict[str, Any]:
        """Check RAG store health."""
        status = {
            "status": "healthy",
            "message": "RAG store is operational"
        }
        
        if self.rag_store is None:
            status["status"] = "unavailable"
            status["message"] = "RAG store not initialized"
            return status
        
        try:
            count = self.rag_store.get_pattern_count()
            status["pattern_count"] = count
            status["message"] = f"RAG store operational with {count} patterns"
        except Exception as e:
            status["status"] = "unhealthy"
            status["message"] = f"RAG store error: {str(e)}"
            logger.error(f"RAG store health check failed: {e}", exc_info=True)
        
        return status
    
    def _check_variable_store(self) -> Dict[str, Any]:
        """Check variable store health."""
        status = {
            "status": "healthy",
            "message": "Variable store is operational"
        }
        
        if self.variable_store is None:
            status["status"] = "unavailable"
            status["message"] = "Variable store not initialized"
            return status
        
        try:
            count = self.variable_store.get_variable_count()
            status["variable_count"] = count
            status["message"] = f"Variable store operational with {count} variables"
        except Exception as e:
            status["status"] = "unhealthy"
            status["message"] = f"Variable store error: {str(e)}"
            logger.error(f"Variable store health check failed: {e}", exc_info=True)
        
        return status
    
    def _check_pattern_learner(self) -> Dict[str, Any]:
        """Check pattern learner health."""
        status = {
            "status": "healthy",
            "message": "Pattern learner is operational"
        }
        
        if self.pattern_learner is None:
            status["status"] = "unavailable"
            status["message"] = "Pattern learner not initialized"
            return status
        
        try:
            stats = self.pattern_learner.get_pattern_stats()
            status["pattern_stats"] = stats
            status["message"] = "Pattern learner operational"
        except Exception as e:
            status["status"] = "unhealthy"
            status["message"] = f"Pattern learner error: {str(e)}"
            logger.error(f"Pattern learner health check failed: {e}", exc_info=True)
        
        return status
    
    def check_readiness(self) -> Dict[str, Any]:
        """
        Check if service is ready to accept requests.
        
        Returns:
            Readiness status dictionary
        """
        readiness = {
            "ready": True,
            "timestamp": datetime.utcnow().isoformat(),
            "checks": {}
        }
        
        # Check if stores are initialized
        readiness["checks"]["rag_store"] = self.rag_store is not None
        readiness["checks"]["variable_store"] = self.variable_store is not None
        readiness["checks"]["pattern_learner"] = self.pattern_learner is not None
        
        # Service is ready if at least stores are initialized
        readiness["ready"] = all(readiness["checks"].values())
        
        return readiness
    
    def check_liveness(self) -> Dict[str, Any]:
        """
        Check if service is alive.
        
        Returns:
            Liveness status dictionary
        """
        return {
            "alive": True,
            "timestamp": datetime.utcnow().isoformat(),
            "uptime_seconds": (datetime.utcnow() - self.start_time).total_seconds()
        }

