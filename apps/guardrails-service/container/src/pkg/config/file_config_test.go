package config

import "testing"

func TestFileValidationIsOnByDefault(t *testing.T) {
	for value, want := range map[string]bool{"": true, "true": true, "false": false} {
		t.Run("FILE_VALIDATION_ENABLED="+value, func(t *testing.T) {
			t.Setenv("FILE_VALIDATION_ENABLED", value)
			if got := LoadConfig().File.Enabled; got != want {
				t.Errorf("File.Enabled = %v, want %v", got, want)
			}
		})
	}
}
