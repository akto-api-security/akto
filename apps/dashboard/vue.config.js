import MonacoWebpackPlugin from "monaco-editor-webpack-plugin"
import path from "path"

module.exports = {
    configureWebpack: {
      plugins: [
        new MonacoWebpackPlugin({
          languages: ["python","yaml","java","javascript"],//configure your languages here
          features: ["coreCommands", "find","folding","bracketMatching","comment","codelens","colorPicker","format","gotoLine"],
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