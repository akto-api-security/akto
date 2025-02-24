const path = require('path')
const webpack = require('webpack')
const ReactRefreshWebpackPlugin = require('@pmmmwh/react-refresh-webpack-plugin')
require("babel-polyfill")

function resolve (dir) {
  return path.join(__dirname, '..', dir)
}

module.exports = {
  mode: 'development',
  entry: {
    "babel-polyfill": "babel-polyfill", 
    main: './web/src/apps/main/index.js',
    'editor.worker': 'monaco-editor/esm/vs/editor/editor.worker.js',
    'json.worker': 'monaco-editor/esm/vs/language/json/json.worker',
  },
  output: {
    path: path.resolve(__dirname, './dist'),
    publicPath: 'http://localhost:3000/dist/',
    filename: '[name].js'
  },
  module: {
    rules: [
      {
        test: /\.css$/,
        use: ['style-loader', 'css-loader'],
      },
      {
        test: /\.scss$/,
        use: ['style-loader', 'css-loader', 'sass-loader'],
      },
      {
        test: /\.sass$/,
        use: [
          'style-loader',
          'css-loader',
          {
            loader: 'sass-loader',
            options: {
              indentedSyntax: true,
              sassOptions: { indentedSyntax: true }
            }
          }
        ]
      },
      {
        test: /\.(js|jsx|ts|tsx)$/,
        exclude: /node_modules\/(?!(tiptap|tiptap-utils|are-you-es5|tiptap-extensions|monaco-yaml|monaco-worker-manager|monaco-marker-data-provider|monaco-editor)\/).*/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: [
              "@babel/preset-env",
              ["@babel/preset-react", {"runtime": "automatic"}],
              "@babel/preset-typescript"
            ],
            plugins: [require.resolve('react-refresh/babel')]
          }
        }
      },
      {
        test: /\.(png|jpg|gif|svg|eot|ttf|woff|woff2)$/,
        type: "asset"
      }
    ]
  },
  resolve: {
    alias: {
      '@': resolve('/web/src')
    },
    extensions: ['*', '.js', '.jsx', '.json', '.ts', '.tsx']
  },
  devServer: {
    port: 3000,
    hot: true,
    headers: {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
      "Access-Control-Allow-Headers": "X-Requested-With, content-type, Authorization"
    },
    historyApiFallback: true,
    client: {
        overlay: true,
    },
  },
  devtool: 'eval-source-map',
  plugins: [
    new ReactRefreshWebpackPlugin(),
  ],
  performance: {
    hints: false
  }
} 