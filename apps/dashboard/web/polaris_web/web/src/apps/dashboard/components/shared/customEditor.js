import { languages } from "monaco-editor/esm/vs/editor/editor.api"

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
                    [/#.*$/, 'comment-http'],
                    [/HTTP\/[0-9.]+/, 'keyword-http'],
                    [/GET|POST|PUT|DELETE|PATCH|OPTIONS|HEAD/, 'keyword-http'],
                    [/"[^"]+":/, 'key-http'], // Match any string in double quotes followed by a colon as JSON key
                    [/".*?"(?=[:,\s}\]])/, 'string-http'], // Match values enclosed in double quotes
                    [/\btrue\b|\bfalse\b|\bnull\b/, 'keyword-http'], // Match JSON keywords (true, false, null)
                    [/[0-9]+/, 'number-http'], // Match numbers
                    [/[{}\[\],]/, 'delimiter-http'], // Match JSON delimiters: {}, [], and commas
                    [/[a-zA-Z-]+(?=:)/, 'key-http'], // Match other keys (e.g., response_type)
                    [/[^:\s]+/, 'value-http'], // Match any remaining text as a value
                  ],
                whitespace: [
                    [/[ \t\r\n]+/, 'white'],
                ],
            },
        });
    }
}

export default editorSetup