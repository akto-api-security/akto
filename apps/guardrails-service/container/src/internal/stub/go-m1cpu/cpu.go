// Package m1cpu is a no-CGo stub that replaces github.com/shoenig/go-m1cpu.
// The real library crashes on macOS 14+ (Sonoma/Sequoia) because Apple
// restricted the IOKit calls it relies on. Guardrails-service doesn't need
// actual CPU frequency data; returning zero/false is harmless here.
package m1cpu

func IsAppleSilicon() bool     { return false }
func PCoreHz() uint64          { return 0 }
func ECoreHz() uint64          { return 0 }
func PCoreGHz() float64        { return 0 }
func ECoreGHz() float64        { return 0 }
func PCoreCount() int          { return 0 }
func ECoreCount() int          { return 0 }
func PCoreCache() (int, int, int) { return 0, 0, 0 }
func ECoreCache() (int, int, int) { return 0, 0, 0 }
func ModelName() string        { return "" }
