import MonacoWebpackPlugin from "monaco-editor-webpack-plugin"
import path from "path"

module.exports = {
    configureWebpack: {
      plugins: [
        new MonacoWebpackPlugin({
          languages: ["python","yaml","java","javascript"],//configure your languages here
          features: ["coreCommands", "find","folding","bracketMatching","comment","codelens","colorPicker","format","gotoLine","indentation",
                     "inlineCompletions", "snippet", "suggest" , "codelens" , "wordHighlighter"],
          customLanguages: [{
              label: 'yaml',
              entry: 'monaco-yaml',
              worker: {
                id: 'monaco-yaml/yamlWorker',
                entry: 'monaco-yaml/yaml.worker',
              },
            },
          ]
        }), // Place it here
      ],
    },
    chainWebpack: (config) => {
      config.resolve.alias.set(
        "vscode",
        path.resolve(
          "./node_modules/monaco-languageclient/lib/vscode-compatibility"
        )
      );
    },
  };