package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/joho/godotenv"
	"mcp-threat-detection/mcp-threat/client"
	"mcp-threat-detection/mcp-threat/config"
	"mcp-threat-detection/mcp-threat/types"
)

var (
	validator *client.MCPValidator
	appConfig *types.AppConfig
)

func main() {
	// Load environment variables
	if err := godotenv.Load(); err != nil {
		log.Println("No .env file found, using environment variables")
	}

	// Load configuration
	var err error
	appConfig, err = config.LoadConfigFromEnv()
	if err != nil {
		log.Fatalf("Failed to load configuration: %v", err)
	}

	// Initialize validator
	validator, err = client.NewMCPValidatorWithConfig(appConfig)
	if err != nil {
		log.Fatalf("Failed to initialize validator: %v", err)
	}
	defer validator.Close()

	// Set Gin mode
	if appConfig.Debug {
		gin.SetMode(gin.DebugMode)
	} else {
		gin.SetMode(gin.ReleaseMode)
	}

	// Create Gin router
	router := gin.Default()

	// Add CORS middleware
	router.Use(cors.New(cors.Config{
		AllowOrigins:     []string{"*"},
		AllowMethods:     []string{"GET", "POST", "OPTIONS"},
		AllowHeaders:     []string{"Origin", "Content-Type", "Accept"},
		ExposeHeaders:    []string{"Content-Length"},
		AllowCredentials: true,
	}))

	// Add middleware
	router.Use(gin.Logger())
	router.Use(gin.Recovery())

	// Root endpoint
	router.GET("/", rootHandler)

	// API routes
	api := router.Group("/api/v1")
	{
		api.POST("/validate", validatePayload)
		api.POST("/validate/request", validateRequest)
		api.POST("/validate/response", validateResponse)
	}

	// Start server
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	srv := &http.Server{
		Addr:    ":" + port,
		Handler: router,
	}

	// Start server in a goroutine
	go func() {
		log.Printf("Starting server on port %s", port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Failed to start server: %v", err)
		}
	}()

	// Wait for interrupt signal
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down server...")

	// Give outstanding requests a deadline for completion
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		log.Fatal("Server forced to shutdown:", err)
	}

	log.Println("Server exited")
}

// rootHandler handles the root endpoint
func rootHandler(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"message": "MCP Threat Detection API",
		"version": "1.0.0",
		"endpoints": []string{
			"POST /api/v1/validate",
			"POST /api/v1/validate/request",
			"POST /api/v1/validate/response",
		},
		"required_inputs": []string{
			"mcp_payload (required)",
			"tool_description (optional)",
		},
		"note": "The /api/v1/validate endpoint automatically detects if the payload is a request or response",
	})
}

// validatePayload validates a payload (generic)
func validatePayload(c *gin.Context) {
	var req types.ValidationRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": fmt.Sprintf("Invalid request: %v", err)})
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(appConfig.LLM.Timeout)*time.Second)
	defer cancel()

	// Use generic validation that auto-detects type
	response := validator.Validate(ctx, req.MCPPayload, req.ToolDescription)

	c.JSON(http.StatusOK, response)
}

// validateRequest validates an MCP request
func validateRequest(c *gin.Context) {
	var req types.ValidationRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": fmt.Sprintf("Invalid request: %v", err)})
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(appConfig.LLM.Timeout)*time.Second)
	defer cancel()

	response := validator.ValidateRequest(ctx, req.MCPPayload, req.ToolDescription)

	c.JSON(http.StatusOK, response)
}

// validateResponse validates an MCP response
func validateResponse(c *gin.Context) {
	var req types.ValidationRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": fmt.Sprintf("Invalid request: %v", err)})
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(appConfig.LLM.Timeout)*time.Second)
	defer cancel()

	response := validator.ValidateResponse(ctx, req.MCPPayload, req.ToolDescription)

	c.JSON(http.StatusOK, response)
} 