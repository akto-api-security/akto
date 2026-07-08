import { useEffect, useRef } from "react"
import { editor } from "monaco-editor/esm/vs/editor/editor.api"
import 'monaco-editor/esm/vs/editor/contrib/find/browser/findController';
import 'monaco-editor/esm/vs/editor/contrib/folding/browser/folding';
import 'monaco-editor/esm/vs/editor/contrib/bracketMatching/browser/bracketMatching';
import 'monaco-editor/esm/vs/basic-languages/json/json.contribution';

// Plain read/write text editor for a remote config file's raw content. Unlike
// YamlEditor.jsx (which registers a custom test-YAML DSL language tied to the
// Test Editor's own state store), this is a small generic wrapper — Monaco's
// built-in "json" mode covers JSON files, and TOML falls back to "plaintext"
// since Monaco has no built-in TOML language.
function ConfigTextEditor({ value, onChange, language = "plaintext", readOnly = false, height = "400px" }) {
    const containerRef = useRef(null)
    const editorInstanceRef = useRef(null)

    useEffect(() => {
        if (!containerRef.current) return
        const instance = editor.create(containerRef.current, {
            value: value || "",
            language,
            readOnly,
            minimap: { enabled: false },
            automaticLayout: true,
            scrollBeyondLastLine: false,
            wordWrap: "on"
        })
        editorInstanceRef.current = instance

        const disposable = instance.onDidChangeModelContent(() => {
            if (onChange) onChange(instance.getValue())
        })

        return () => {
            disposable.dispose()
            instance.dispose()
        }
        // Only re-create the editor when the language/readOnly mode changes —
        // `value` updates below are applied imperatively so typing doesn't get
        // clobbered by a re-render.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [language, readOnly])

    useEffect(() => {
        const instance = editorInstanceRef.current
        if (instance && value !== undefined && value !== instance.getValue()) {
            instance.setValue(value || "")
        }
    }, [value])

    return <div ref={containerRef} style={{ height, border: "1px solid var(--p-color-border)" }} />
}

export default ConfigTextEditor
