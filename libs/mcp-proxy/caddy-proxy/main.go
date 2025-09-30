package main

import (
	caddycmd "github.com/caddyserver/caddy/v2/cmd"

	// plug in Caddy modules here
	_ "github.com/caddyserver/caddy/v2/modules/standard"

	// Import our threat detector module - THIS EMBEDS IT INTO THE BINARY
	_ "github.com/akto-api-security/akto/libs/mcp-proxy/caddy-proxy/threatdetector"
)

func main() {
	// This runs Caddy with our custom module compiled in
	// No separate processes, no HTTP calls - everything runs in this single binary
	caddycmd.Main()
}
