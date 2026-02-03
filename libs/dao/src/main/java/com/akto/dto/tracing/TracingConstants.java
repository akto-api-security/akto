package com.akto.dto.tracing;

public final class TracingConstants {

    private TracingConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    public static final class SpanKind {
        public static final String WORKFLOW = "workflow";
        public static final String AGENT = "agent";
        public static final String LLM = "llm";
        public static final String TOOL = "tool";
        public static final String MCP_SERVER = "mcp_server";
        public static final String RAG = "rag";
        public static final String VECTOR_DB = "vector_db";
        public static final String EMBEDDING = "embedding";
        public static final String RERANKER = "reranker";
        public static final String API = "api";
        public static final String HTTP = "http";
        public static final String DATABASE = "database";
        public static final String CACHE = "cache";
        public static final String QUEUE = "queue";
        public static final String STREAM = "stream";
        public static final String FUNCTION = "function";
        public static final String TASK = "task";
        public static final String BATCH = "batch";
        public static final String CUSTOM = "custom";
        public static final String UNKNOWN = "unknown";
        private SpanKind() {}
    }

    public static final class ServiceType {
        public static final String OPENAI = "openai";
        public static final String ANTHROPIC = "anthropic";
        public static final String COHERE = "cohere";
        public static final String HUGGINGFACE = "huggingface";
        public static final String GOOGLE_GEMINI = "google_gemini";
        public static final String AZURE_OPENAI = "azure_openai";
        public static final String AWS_BEDROCK = "aws_bedrock";
        public static final String GROQ = "groq";
        public static final String MISTRAL = "mistral";
        public static final String PINECONE = "pinecone";
        public static final String WEAVIATE = "weaviate";
        public static final String QDRANT = "qdrant";
        public static final String CHROMA = "chroma";
        public static final String MILVUS = "milvus";
        public static final String FAISS = "faiss";
        public static final String MCP_FILESYSTEM = "mcp_filesystem";
        public static final String MCP_DATABASE = "mcp_database";
        public static final String MCP_SEARCH = "mcp_search";
        public static final String MCP_BROWSER = "mcp_browser";
        public static final String MCP_CUSTOM = "mcp_custom";
        public static final String MONGODB = "mongodb";
        public static final String POSTGRESQL = "postgresql";
        public static final String MYSQL = "mysql";
        public static final String REDIS = "redis";
        public static final String ELASTICSEARCH = "elasticsearch";
        public static final String AWS_S3 = "aws_s3";
        public static final String AWS_LAMBDA = "aws_lambda";
        public static final String GCP_STORAGE = "gcp_storage";
        public static final String AZURE_BLOB = "azure_blob";
        public static final String CUSTOM = "custom";
        public static final String UNKNOWN = "unknown";
        private ServiceType() {}
    }

}
