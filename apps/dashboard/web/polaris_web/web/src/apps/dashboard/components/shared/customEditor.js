import { editor, languages } from "monaco-editor/esm/vs/editor/editor.api"

const LANGUAGE_ID = 'custom_http'
const editorSetup = {
    registerLanguage: function(){
        languages.register({id: LANGUAGE_ID})
    },
    setTokenizer: function(keywords,symbols){
        languages.setLanguageConfiguration(LANGUAGE_ID, {
            brackets: [
              ['{', '}'],
              ['[', ']'],
            ],
            autoClosingPairs: [
              { open: '{', close: '}' },
              { open: '[', close: ']' },
              { open: '"', close: '"', notIn: ['string'] },
        ],});
        languages.setMonarchTokensProvider(LANGUAGE_ID, {
            keywords: keywords,
            symbols: symbols,
            tokenizer: {
                root: [
                    [/#.*$/, 'comment'],
                    [/HTTP\/[0-9.]+/, 'keyword'],
                    [/GET|POST|PUT|DELETE|PATCH/, 'keyword'],
                    [/"[^"]+":/, 'key'], // Match any string in double quotes followed by a colon as JSON key
                    [/".*?"(?=[:,\s}\]])/, 'string'], // Match values enclosed in double quotes
                    [/\btrue\b|\bfalse\b|\bnull\b/, 'keyword'], // Match JSON keywords (true, false, null)
                    [/[0-9]+/, 'number'], // Match numbers
                    [/[{}\[\],]/, 'delimiter'], // Match JSON delimiters: {}, [], and commas
                    [/[a-zA-Z-]+(?=:)/, 'key'], // Match other keys (e.g., response_type)
                    [/[^:\s]+/, 'value'], // Match any remaining text as a value
                  ],
                whitespace: [
                    [/[ \t\r\n]+/, 'white'],
                ],
            },
        });
    },

    setEditorTheme: function(){
        editor.defineTheme("customTheme", {
            base: "vs",
            inherit: false,
            rules: [
                { token: 'comment', foreground: '#008800', fontStyle: 'italic' },
                { token: 'keyword', foreground: '#0000FF' },
                { token: 'key', foreground: '#A31515'}, // Color for keys
                { token: 'jsonkey', foreground: '#A31515' }, // Color for strings
                { token: 'number', foreground: '#0451A5', fontStyle: "bold" }, // Color for numbers
                { token: 'value', foreground: '#0451A5' }, // Color for values
                { token: 'delimeter', foreground: '#000000'},
                { token: 'default', foreground: '#0451A5'}
            ],
                colors: {
                    'editorLineNumber.foreground': '#000000',
                    'editorLineNumber.activeForeground': '#000000',
                    'editorIndentGuide.background': '#D3D3D3'
                }
        });
    },

}

export default editorSetup