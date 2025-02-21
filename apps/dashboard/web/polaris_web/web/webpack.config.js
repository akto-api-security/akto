var path = require('path')
var webpack = require('webpack')
require("babel-polyfill")
const TerserPlugin = require("terser-webpack-plugin");

function resolve (dir) {
      return path.join(__dirname, '..', dir)
}

function hashGenerator() {
  return 1
}

process.env.HASH = hashGenerator()

module.exports = {
  entry: {
    "babel-polyfill": "babel-polyfill",
    main: './web/src/apps/main/index.js',
    'editor.worker': 'monaco-editor/esm/vs/editor/editor.worker.js',
    'json.worker': 'monaco-editor/esm/vs/language/json/json.worker',
  },
  output: {
    path: path.resolve(__dirname, './dist'),
    publicPath: process.env.VERSION ==='' || process.env.VERSION.includes("akto-release-version") ? '/polaris_web/web/dist/':  'https://d1hvi6xs55woen.cloudfront.net/polaris_web/' + process.env.VERSION +  '/dist/',
    filename: '[name].js'
  },
  module: {
    rules: [
      {
        test: /\.css$/,
        use: [
          'style-loader',
          'css-loader'
        ],
      },
      {
        test: /\.scss$/,
        use: [
          'style-loader',
          'css-loader',
          'sass-loader'
        ],
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
              // sass-loader version >= 8
              sassOptions: {
                indentedSyntax: true
              }
            }
          }
        ]
      },
      {
        test: /\.(ts|tsx|js|jsx)$/,
        include: resolve(__dirname, 'src'),
        exclude: /node_modules/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: [
              "@babel/preset-env",
              ["@babel/preset-react", {"runtime": "automatic"}],
              "@babel/preset-typescript"
            ]
          }
        }
      },
      {
        test: /\.(png|jpg|gif|svg|eot|ttf|woff|woff2)$/,
        type:"asset"
      }
    ]
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, '/web/src')
    },
    extensions: ['.ts', '.tsx', '.js', '.jsx', '.json', '*']
  },
  devServer: {
    historyApiFallback: true,
    noInfo: true,
    overlay: true
  },
  performance: {
    hints: false
  },
  devtool: 'eval-source-map'
}

if (process.env.NODE_ENV === 'production') {
  module.exports.devtool = 'source-map'
  // http://vue-loader.vuejs.org/en/workflow/production.html
  module.exports.plugins = (module.exports.plugins || []).concat([
    new webpack.DefinePlugin({
      'process.env': {
        NODE_ENV: '"production"'
      }
    }),
    new TerserPlugin({
      "terserOptions":{
        compress:true,
        sourceMap: true,
        output:{
          ascii_only:true
        }
      }
    }),
    new webpack.LoaderOptionsPlugin({
      minimize: true
    }),
  ])
}
