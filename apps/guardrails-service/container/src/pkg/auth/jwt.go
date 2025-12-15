package auth

import (
	"os"
)

// GetDatabaseAbstractorServiceToken returns the JWT token from environment variable
// This token is used for authenticating with database-abstractor service
func GetDatabaseAbstractorServiceToken() string {
	return os.Getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN")
}
