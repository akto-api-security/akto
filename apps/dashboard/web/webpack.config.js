var path = require('path')
var webpack = require('webpack')
require("babel-polyfill")
const TerserPlugin = require("terser-webpack-plugin");
const { GenerateSW } = require("workbox-webpack-plugin");
const MonacoWebpackPlugin = require('monaco-editor-webpack-plugin');

function resolve (dir) {
      return path.join(__dirname, '..', dir)
}

function hashGenerator() {
  return 1
}

process.env.HASH = hashGenerator()

module.exports = {
  entry: {"babel-polyfill": "babel-polyfill", main: './web/src/apps/main/index.js',
  'editor.worker': 'monaco-editor/esm/vs/editor/editor.worker.js',
  'json.worker': 'monaco-editor/esm/vs/language/json/json.worker',
  // 'css.worker': 'monaco-editor/esm/vs/language/css/css.worker',
  // 'html.worker': 'monaco-editor/esm/vs/language/html/html.worker',
  // 'ts.worker': 'monaco-editor/esm/vs/language/typescript/ts.worker'
},
  output: {
    path: path.resolve(__dirname, './dist'),
    publicPath: '/dist/',
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
        test: /\.(js|jsx)$/,
        exclude: /node_modules\/(?!(tiptap|tiptap-utils|are-you-es5|tiptap-extensions|monaco-yaml|monaco-worker-manager|monaco-marker-data-provider|monaco-editor)\/).*/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: [
              "@babel/preset-env",
             ["@babel/preset-react", {"runtime": "automatic"}]
          ]
          }
        }

      },
      {
        test: /\.(png|jpg|gif|svg|eot|ttf|woff|woff2)$/,
        loader: 'file-loader',
        options: {
          name: 'images/[name].[ext]?[hash]'
        }
      }
    ]
  },
  resolve: {
    alias: {
      '@': resolve('/web/src')
    },
    extensions: ['*', '.js', '.jsx', '.json']
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
    // new GenerateSW({
    //    exclude: [/\.map$/, /asset-manifest\.json$/],
    //  }),
    // new SWPrecacheWebpackPlugin({
    //   cacheId: 'akto-app',
    //   filename: 'sw.js',
    //   staticFileGlobs: ['dist/**/*.{js,css}', '/'],
    //   minify: true,
    //   stripPrefix: 'dist/',
    //   dontCacheBustUrlsMatching: /\.\w{6}\./
    // }),
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
    // new MonacoWebpackPlugin({
    //   languages:["json"],
    //   publicPath:"/"
    // })
  ])
}
