package policies

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"time"

	"gopkg.in/yaml.v3"
)

// FilePolicyManager implements PolicyManager interface for file-based policy loading
type FilePolicyManager struct {
	policiesDir string
	policies    []Policy
	lastLoad    time.Time
	mutex       sync.RWMutex
}

// NewFilePolicyManager creates a new file-based policy manager
func NewFilePolicyManager(policiesDir string) *FilePolicyManager {
	return &FilePolicyManager{
		policiesDir: policiesDir,
		policies:    []Policy{},
	}
}

// LoadPolicies loads all policies from the policies directory
func (fpm *FilePolicyManager) LoadPolicies() ([]Policy, error) {
	fpm.mutex.Lock()
	defer fpm.mutex.Unlock()

	policies := []Policy{}

	// Check if policies directory exists
	if _, err := os.Stat(fpm.policiesDir); os.IsNotExist(err) {
		log.Printf("Policies directory does not exist: %s", fpm.policiesDir)
		return policies, nil // Return empty list, not an error
	}

	// Read all .yaml files in the policies directory
	files, err := filepath.Glob(filepath.Join(fpm.policiesDir, "*.yaml"))
	if err != nil {
		return nil, fmt.Errorf("failed to read policies directory: %v", err)
	}

	if len(files) == 0 {
		log.Printf("No policy files found in directory: %s", fpm.policiesDir)
		return policies, nil
	}

	log.Printf("Loading %d policy files from %s", len(files), fpm.policiesDir)

	for _, file := range files {
		policy, err := fpm.loadPolicyFromFile(file)
		if err != nil {
			log.Printf("Failed to load policy from %s: %v", file, err)
			continue // Skip invalid files, continue with others
		}

		if policy.ID == "" {
			log.Printf("Policy in %s has no ID, skipping", file)
			continue
		}

		policies = append(policies, policy)
		log.Printf("Loaded policy: %s", policy.ID)
	}

	// Sort policies by ID for consistent ordering
	sort.Slice(policies, func(i, j int) bool {
		return policies[i].ID < policies[j].ID
	})

	fpm.policies = policies
	fpm.lastLoad = time.Now()

	log.Printf("Successfully loaded %d policies", len(policies))
	return policies, nil
}

// loadPolicyFromFile loads a single policy from a YAML file
func (fpm *FilePolicyManager) loadPolicyFromFile(filePath string) (Policy, error) {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return Policy{}, fmt.Errorf("failed to read file %s: %v", filePath, err)
	}

	var policy Policy
	err = yaml.Unmarshal(data, &policy)
	if err != nil {
		return Policy{}, fmt.Errorf("failed to parse YAML from %s: %v", filePath, err)
	}

	return policy, nil
}

// ReloadPolicies reloads policies from the file system
func (fpm *FilePolicyManager) ReloadPolicies() error {
	return nil // For file-based loading, just call LoadPolicies again
}

// GetPolicyByID returns a policy by its ID
func (fpm *FilePolicyManager) GetPolicyByID(id string) (*Policy, error) {
	fpm.mutex.RLock()
	defer fpm.mutex.RUnlock()

	for _, policy := range fpm.policies {
		if policy.ID == id {
			return &policy, nil
		}
	}

	return nil, fmt.Errorf("policy with ID '%s' not found", id)
}

// IsPolicyEnabled checks if a policy is enabled (for file-based loading, all loaded policies are enabled)
func (fpm *FilePolicyManager) IsPolicyEnabled(id string) bool {
	fpm.mutex.RLock()
	defer fpm.mutex.RUnlock()

	for _, policy := range fpm.policies {
		if policy.ID == id {
			return true // All loaded policies are considered enabled
		}
	}

	return false
}

// GetLoadedPolicies returns the currently loaded policies (for debugging/inspection)
func (fpm *FilePolicyManager) GetLoadedPolicies() []Policy {
	fpm.mutex.RLock()
	defer fpm.mutex.RUnlock()

	// Return a copy to prevent external modification
	policies := make([]Policy, len(fpm.policies))
	copy(policies, fpm.policies)
	return policies
}

// GetLastLoadTime returns when policies were last loaded
func (fpm *FilePolicyManager) GetLastLoadTime() time.Time {
	fpm.mutex.RLock()
	defer fpm.mutex.RUnlock()
	return fpm.lastLoad
}
