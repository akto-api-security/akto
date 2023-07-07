import { editor, languages, MarkerSeverity } from "monaco-editor/esm/vs/editor/editor.api"
import leven from "leven"

const editorSetup = {

    registerLanguage: function(){
        languages.register({id: 'custom_yaml'})
    },

    setTokenizer: function(keywords,symbols){
        languages.setMonarchTokensProvider("custom_yaml", {
            keywords: keywords,
            symbols: symbols,
            tokenizer: {
                root: [
                    [/[a-z_$][\w$]*/, { cases: { 
                                '@keywords': 'keyword',
                                '@default': 'identifier' } }
                    ],
                    [/[A-Z][\w\$]*/, 'type.identifier' ],
                    // [/\$\{[a-zA-Z0-9]\}\$]*/, 'variable' ],
                    { include: '@whitespace' },
                    [/[{}()\[\]]/, '@brackets'],
                    [/[<>](?!@symbols)/, '@brackets'],
                    [/@\s*[a-zA-Z_\$][\w\$]*/, { token: 'annotation', log: 'annotation token: $0' }],
                    [/\d*\.\d+([eE][\-+]?\d+)?/, 'number.float'],
                    [/0[xX][0-9a-fA-F]+/, 'number.hex'],
                    [/\d+/, 'number'],
                    [/[;,.]/, 'delimiter'],
                    [/"([^"\\]|\\.)*$/, 'string.invalid' ],
                    [/"/,  { token: 'string.quote', bracket: '@open', next: '@string' } ],
                    [/'[^\\']'/, 'string'],
                    [/'/, 'string.invalid'],
                    [/^\s*#.*/, 'comment']
                ],
                whitespace: [
                    [/[ \t\r\n]+/, 'white'],
                ],
                string: [
                    [/[^\\"]+/,  'string'],
                    [/\\./,      'string.escape.invalid'],
                    [/"/,        { token: 'string.quote', bracket: '@close', next: '@pop' } ]
                ],
            },
        });
    },

    setEditorTheme: function(){
        editor.defineTheme("customTheme", {
            base: "vs",
            inherit: false,
            rules: [
                { token: "keyword", foreground: "#008080", fontStyle: "bold" },
                { token: "comment", foreground: "#008000" },
                { token: "string", foreground: "#0451a5" },
                { token: "identifier", foreground: "#0451a5" },
                { token: "number", foreground: "#6200ea"},
                { token: "variable", foreground: "#bada55"}
            ],
                colors: {
                    'editorLineNumber.foreground': '#999999',
                    'editorLineNumber.activeForeground': '#000000',
                    'editorIndentGuide.background': '#D3D3D3'
                }
        });
    },

    setAutoComplete: function(keywords){
        languages.registerCompletionItemProvider('custom_yaml', {
            provideCompletionItems: (model,position) => {
                const word = model.getWordUntilPosition(position);
                const range = {
                    startLineNumber: position.lineNumber,
                    endLineNumber: position.lineNumber,
                    startColumn: word.startColumn,
                    endColumn: word.endColumn,
                };
                const suggestions = [
                    ...keywords.map(word=>{
                        return{
                            label: word,
                            kind: languages.CompletionItemKind.Keyword,
                            insertText: word,
                        }
                    }),
                    {
                        label: "ifelse",
                        kind: languages.CompletionItemKind.Snippet,
                        insertText: [
                            "if (${1:condition}) {",
                            "\t$0",
                            "} else {",
                            "\t",
                            "}",
                        ].join("\n"),
                        insertTextRules:
                            languages.CompletionItemInsertTextRule
                                .InsertAsSnippet,
                        documentation: "If-Else Statement",
                        range: range,
                    },
                ];
                return {suggestions : suggestions}
            }
        })
    },

    findErrors: function(Editor,keywords){
        Editor.onDidChangeModelContent(() => {
            const model = Editor.getModel();
            const markers = model.getValue().split('\n').flatMap((line, index) => {
                const words = line.split(/\s+/); // Split the line into words
                const errors = words.flatMap((word, wordIndex) => {
                    const matchingKeywords = keywords.filter(keyword => {
                        const distance = leven(keyword, word);
                        return distance > 0 && distance < 2 && word.length >= 4;
                    }); // Adjust the distance threshold as needed
                    return matchingKeywords.map(keyword => ({
                        severity: MarkerSeverity.Error,
                        message: `Invalid keyword: ${word}. Did you mean: ${keyword}?`,
                        startLineNumber: index + 1,
                        startColumn: line.indexOf(word) + 1,
                        endLineNumber: index + 1,
                        endColumn: line.indexOf(word) + word.length + 1,
                    }));
                });
                return errors;
            });
            editor.setModelMarkers(model, 'keyword-marker-owner', markers)

        })
    }

}

export default editorSetup