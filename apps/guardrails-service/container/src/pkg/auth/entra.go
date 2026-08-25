package auth

import (
	"context"
	"fmt"
	"net/http"
	"strings"

	"github.com/MicahParks/keyfunc/v3"
	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"go.uber.org/zap"
)

// NewEntraMiddleware builds a gin.HandlerFunc that authenticates callers of the
// Copilot Studio webhook endpoints by verifying a Microsoft Entra ID bearer
// token: signature via the tenant's published JWKS, issuer, and audience
// (the exposed scope registered for this deployment's app registration).
//
// This is purely a caller-authentication gate for the Copilot Studio webhook
// contract — it proves the request came from the configured Entra tenant and
// nothing more. It does not resolve or influence the Akto account; account
// resolution for these endpoints uses the same applyAuthenticatedAccount path
// (JWT/service-token account override) every other endpoint in this service
// already uses.
func NewEntraMiddleware(tenantID, audience string, logger *zap.Logger) (gin.HandlerFunc, error) {
	if strings.TrimSpace(tenantID) == "" {
		return nil, fmt.Errorf("entra tenant id is empty")
	}
	if strings.TrimSpace(audience) == "" {
		return nil, fmt.Errorf("entra audience is empty")
	}

	jwksURL := fmt.Sprintf("https://login.microsoftonline.com/%s/discovery/v2.0/keys", tenantID)
	k, err := keyfunc.NewDefaultCtx(context.Background(), []string{jwksURL})
	if err != nil {
		return nil, fmt.Errorf("failed to load Entra ID JWKS from %s: %w", jwksURL, err)
	}

	issuer := fmt.Sprintf("https://login.microsoftonline.com/%s/v2.0", tenantID)

	return func(c *gin.Context) {
		tokenString := extractBearerToken(c.GetHeader("Authorization"))
		if tokenString == "" {
			logger.Warn("Entra authentication failed: missing bearer token")
			c.AbortWithStatus(http.StatusUnauthorized)
			return
		}

		_, err := jwt.Parse(tokenString, k.Keyfunc,
			jwt.WithValidMethods([]string{"RS256"}),
			jwt.WithIssuer(issuer),
			jwt.WithAudience(audience),
		)
		if err != nil {
			logger.Warn("Entra authentication failed", zap.Error(err))
			c.AbortWithStatus(http.StatusUnauthorized)
			return
		}

		c.Next()
	}, nil
}

func extractBearerToken(authHeader string) string {
	const prefix = "bearer "
	if len(authHeader) < len(prefix) || !strings.EqualFold(authHeader[:len(prefix)], prefix) {
		return ""
	}
	return strings.TrimSpace(authHeader[len(prefix):])
}
