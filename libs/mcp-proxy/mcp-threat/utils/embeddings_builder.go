package utils

import (
	"encoding/gob"
	"fmt"
	"log"
	"os"
)

func SaveEmbeddings(filename string, embeddings map[string][]float32) error {
	f, err := os.Create(filename)
	if err != nil {
		return err
	}
	defer f.Close()
	return gob.NewEncoder(f).Encode(embeddings)
}

// LoadEmbeddings loads the map of keyword embeddings from a .gob file
func LoadEmbeddings(filename string) (map[string][]float32, error) {
	file, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	var embeds map[string][]float32
	decoder := gob.NewDecoder(file)
	if err := decoder.Decode(&embeds); err != nil {
		return nil, err
	}

	return embeds, nil
}

func main1() {
	// Paths for ONNX and tokenizer
	tokenizerPath := "models/tokenizer.json"
	modelPath := "models/model.onnx"
	libonnxPath := "/opt/homebrew/Cellar/onnxruntime/1.22.2_2/lib/libonnxruntime.dylib"

	// Initialize SemanticDetector without precomputed keywords
	detector, err := NewSemanticDetector(tokenizerPath, modelPath, libonnxPath, nil, 0.75)
	if err != nil {
		log.Fatalf("Failed to initialize SemanticDetector: %v", err)
	}
	defer detector.Close()

	// Your list of keywords
	keywords := []string{
		"/etc/passwd",
		"/etc/shadow",
		"/etc/sudoers",
		"/etc/hosts",
		"/etc/crontab",
		"/root/",
		"/proc/",
		"/sys/",
		"/dev/random",
		"/dev/urandom",
		"/etc/environment",

		// Windows paths and env
		`C:\\Windows\\System32`,
		`C:\\Users\\Administrator`,
		"%SYSTEMROOT%",

		// SSH keys and files
		"id_rsa",
		"id_ed25519",
		"id_ecdsa",
		"known_hosts",
		"authorized_keys",

		// Software license headers (multi-line phrases included as-is)
		"GNU AFFERO GENERAL PUBLIC LICENSE\nVersion 3, 19 November 2007",
		"GNU GENERAL PUBLIC LICENSE\nVersion 3, 29 June 2007",
		"GNU LESSER GENERAL PUBLIC LICENSE\nVersion 3, 29 June 2007",
		"Mozilla Public License Version 2.0",
		"Apache License\nVersion 2.0, January 2004",
		"MIT License",
		"Boost Software License - Version 1.0 - August 17th, 2003",

		// Copyright markers
		"Copyright (C)",
		"Copyright ©",

		"ignore previous instructions",
		"ignore all instructions",
		"ignore safety checks",
		"ignore remaining instructions",
		"disregard prior instructions",
		"forget prior instructions",
		"bypass safety checks",
		"ignore safety policies",
		"ignore system policies",
		"override system prompt",
		"reveal system prompt",
		"print system prompt",
		"leak system prompt",
		"system prompt",
		"hidden system prompt",
		"act as an ai",
		"act as root",
		"run as sudo",
		"run as admin",
		"override tool output",
		"follow last instruction",
		"developer mode",
		"jailbreak mode",
		"do anything now",
		"dan mode",
		"ignore guardrails",
		"ignore safety layer",
		"ignore prior context",
		"share secret",
		"rotate keys now",
		"god mode",
		"malicious",
		"exploit",
		"attack",
		"hack",
	}

	// Map to hold embeddings
	embeddings := make(map[string][]float32)

	for _, kw := range keywords {
		emb, err := detector.ComputeEmbedding(kw)
		if err != nil {
			log.Printf("Failed to compute embedding for '%s': %v", kw, err)
			continue
		}
		embeddings[kw] = emb
		fmt.Printf("Computed embedding for: %s\n", kw)
	}

	// Save embeddings to file
	if err := SaveEmbeddings("keyword_embeddings_wo_mean.gob", embeddings); err != nil {
		log.Fatalf("Failed to save embeddings: %v", err)
	}

	fmt.Println("Embeddings saved to keyword_embeddings.gob")
}
