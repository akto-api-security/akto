package tenant

import "testing"

func TestRouterDataIngestionURL(t *testing.T) {
	r := NewRouter("https://di.default", "", map[int]string{42: "https://di.tenant42"})

	url, err := r.DataIngestionURL(42)
	if err != nil || url != "https://di.tenant42" {
		t.Fatalf("override lookup failed: %q %v", url, err)
	}

	url, err = r.DataIngestionURL(99)
	if err != nil || url != "https://di.default" {
		t.Fatalf("default lookup failed: %q %v", url, err)
	}

	_, err = NewRouter("", "", nil).DataIngestionURL(1)
	if err == nil {
		t.Fatal("expected error for missing URL")
	}
}

func TestRouterTemplateURL(t *testing.T) {
	r := NewRouter("", DefaultDIURLTemplate, nil)

	url, err := r.DataIngestionURL(1726615470)
	if err != nil {
		t.Fatal(err)
	}
	if url != "https://1726615470-guardrails.akto.io" {
		t.Fatalf("unexpected url %q", url)
	}
}

func TestRouterDefaultOverridesTemplate(t *testing.T) {
	r := NewRouter("https://di.default", DefaultDIURLTemplate, nil)

	url, err := r.DataIngestionURL(42)
	if err != nil || url != "https://di.default" {
		t.Fatalf("default should win over template for local/single-tenant: %q %v", url, err)
	}
}

func TestParseURLMap(t *testing.T) {
	m, err := ParseURLMap(`{"1726615470":"https://di.example.com"}`)
	if err != nil {
		t.Fatal(err)
	}
	if m[1726615470] != "https://di.example.com" {
		t.Fatalf("unexpected map: %#v", m)
	}
}
