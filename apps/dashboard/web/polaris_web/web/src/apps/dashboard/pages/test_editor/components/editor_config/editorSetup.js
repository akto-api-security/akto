import { editor, languages, MarkerSeverity } from "monaco-editor/esm/vs/editor/editor.api"
import leven from "leven"
import * as yamlConf from "monaco-editor/esm/vs/basic-languages/yaml/yaml" 
import snippets from "./snippets"
import keywordSnippets from "./keywordSnippets"

let { conf,language } = yamlConf;

function getLanguage() {

    let keywordCondition = [
        /.+?(?=(\s+#|$))/,
        {
            cases: {
                "@keywords": "keyword",
                "@default": "rawString"
            }
        }
    ] 
    // autocomplete doesn't work for strings.
    // so this essentially fools the engine to think that the unquoted strings are not strings.
    language.tokenizer.root.pop();
    language.tokenizer.array.pop();
    language.tokenizer.object.pop();
    language.tokenizer.root.push(keywordCondition);
    keywordCondition[0] = /[^\],]+/
    language.tokenizer.array.push(keywordCondition);
    keywordCondition[0] = /[^\},]+/
    language.tokenizer.object.push(keywordCondition);

    return language;
}

const editorSetup = {

    registerLanguage: function(){
        languages.register({id: 'custom_yaml'})
    },

    setTokenizer: function(){
        languages.setMonarchTokensProvider("custom_yaml", getLanguage());
        languages.setLanguageConfiguration("custom_yaml", conf);
    },

    // taking https://github.com/Microsoft/monaco-editor/issues/338 this as reference
    // monaco editor doesn't support multiple custom themes to be rendered simultaneosuly
    // thus here we have changed the name of the token
    
    setEditorTheme: function(){
        editor.defineTheme("customTheme", {
            base: "vs",
            inherit: true,
            rules: [
                { token: "keyword", foreground: "#0000ff" },
                { token: "type", foreground: "#008080" },
                { token: "comment", foreground: "#008000" },
                { token: "string", foreground: "#0451a5" },
                { token: "rawString", foreground: "#0451a5" },
                { token: "identifier", foreground: "#0451a5" },
                { token: "number", foreground: "#098658"},
                { token: 'comment-http', foreground: '#008800', fontStyle: 'italic' },
                { token: 'keyword-http', foreground: '#0000FF' },
                { token: 'key-http', foreground: '#A31515'}, // Color for keys
                { token: 'string-http', foreground: '0451A5' }, // Assuming you meant this for string values
                { token: 'number-http', foreground: '#0451A5', fontStyle: "bold" }, // Color for numbers
                { token: 'value-http', foreground: '#0451A5' }, // Color for values
                { token: 'delimiter-http', foreground: '#000000'}, // Corrected typo here
                { token: 'default', foreground: '#0451A5'} 
            ],
                colors: {
                    'editorLineNumber.foreground': '#999999',
                    'editorLineNumber.activeForeground': '#000000',
                    'editorIndentGuide.background': '#D3D3D3',
                    'editor.background': '#FAFBFB'
                }
        });
    },

    setAutoComplete: function(keywords){
        languages.registerCompletionItemProvider('custom_yaml', {
            provideCompletionItems: (model,position) => {
                const word = model.getWordUntilPosition(position);
                let range = {
                    startLineNumber: position.lineNumber,
                    endLineNumber: position.lineNumber,
                    startColumn: 1,
                    endColumn: word.endColumn,
                };
                const currentLine = model.getValueInRange(range);
                if (currentLine.includes(":")) {
                    return {
                        suggestions: [
                            {
                                label: "",
                                kind: languages.CompletionItemKind.Keyword,
                                insertText: ""
                            }
                        ]
                    }
                }

                const suggestions = [
                    ...keywords.map(word=>{
                        return{
                            label: word,
                            kind: languages.CompletionItemKind.Keyword,
                            insertText: keywordSnippets[word] ? keywordSnippets[word].join("\n") : word,
                            insertTextRules:
                                languages.CompletionItemInsertTextRule
                                    .InsertAsSnippet,
                        }
                    }),
                    ...snippets.map(snippet => {
                        return {
                            label: snippet.label,
                            kind: languages.CompletionItemKind.Snippet,
                            insertText: snippet.text.join("\n"),
                            insertTextRules:
                                languages.CompletionItemInsertTextRule
                                    .InsertAsSnippet,
                            documentation: snippet.desc,
                            range: range,
                        }
                    })
                ];
                return {suggestions : suggestions}
            }
        })
    },

    findErrors: function(Editor, keywords){
        let keyRegex = /^\s*-?\s*(\w+)\s*:( *)$/;
        Editor.onDidChangeModelContent(() => {
            const model = Editor.getModel();
            const markers = model.getValue().split('\n').flatMap((line, index) => {
                let match = keyRegex.exec(line);
                const words = [];
                if (match != null) {
                    words.push(match[1]);
                }
                const errors = words.flatMap((word, wordIndex) => {
                    const matchingKeywords = keywords.filter(keyword => {
                        const distance = leven(keyword, word);
                        return distance > 0 && distance < 3 && word.length >= 2 && !keywords.includes(word);
                    });
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
            editor.setModelMarkers(model, 'keyword-marker-owner', markers);
        });
    }
    

}

export default editorSetup