package strutil

// FirstNonEmpty returns the first non-empty value for keys in attrs.
func FirstNonEmpty(attrs map[string]string, keys ...string) string {
	for _, k := range keys {
		if v := attrs[k]; v != "" {
			return v
		}
	}
	return ""
}
