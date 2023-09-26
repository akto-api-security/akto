var path = require('path')
var webpack = require('webpack')
require("babel-polyfill")
const UglifyJsPlugin = require('uglifyjs-webpack-plugin')
const SWPrecacheWebpackPlugin = require('sw-precache-webpack-plugin')


function resolve (dir) {
      return path.join(__dirname, '..', dir)
}

function hashGenerator() {
  return 1
}

process.env.HASH = hashGenerator()

module.exports = {
  entry: {"babel-polyfill": "babel-polyfill", main: './web/src/apps/main/main.js', chrome_plugin: './web/src/apps/chrome_plugin/main.js'},
  output: {
    path: path.resolve(__dirname, './dist'),
    // the path here and in login.jsp are different.
    publicPath: process.env.VERSION ==='' || process.env.VERSION.includes("akto-release-version") ? '/dist/':  'https://d1hvi6xs55woen.cloudfront.net/web/' + process.env.VERSION +  '/dist/',
    filename: '[name].js'
  },
  module: {
    rules: [
      {
        test: /\.css$/,
        use: [
          'vue-style-loader',
          'css-loader'
        ],
      },
      {
        test: /\.scss$/,
        use: [
          'vue-style-loader',
          'css-loader',
          'sass-loader'
        ],
      },
      {
        test: /\.sass$/,
        use: [
          'vue-style-loader',
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
        test: /\.vue$/,
        loader: 'vue-loader',
        options: {
          loaders: {
            // Since sass-loader (weirdly) has SCSS as its default parse mode, we map
            // the "scss" and "sass" values for the lang attribute to the right configs here.
            // other preprocessors should work out of the box, no loader config like this necessary.
            'scss': [
              'vue-style-loader',
              'css-loader',
              {
                loader: 'sass-loader',
                options: {
                  // sass-loader version >= 8
                  sassOptions: {
                    indentedSyntax: true
                  }
                }
              }
            ],
            'sass': [
              'vue-style-loader',
              'css-loader',
              'sass-loader?indentedSyntax'
            ]
          }
          // other vue-loader options go here
        }
      },
      {
        test: /\.(js|jsx)$/,
        exclude: /node_modules\/(?!(tiptap|tiptap-utils|are-you-es5|tiptap-extensions|monaco-yaml|monaco-worker-manager|monaco-marker-data-provider|monaco-editor)\/).*/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: ['@babel/preset-env']
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
      'vue$': 'vue/dist/vue.esm.js',
      '@': resolve('/web/src')
    },
    extensions: ['*', '.js', '.vue', '.json']
  },
  devServer: {
    historyApiFallback: true,
    noInfo: true,
    overlay: true
  },
  performance: {
    hints: false
  },
  devtool: '#eval-source-map'
}

if (process.env.NODE_ENV === 'production') {
  module.exports.devtool = '#source-map'
  // http://vue-loader.vuejs.org/en/workflow/production.html
  module.exports.plugins = (module.exports.plugins || []).concat([
    new SWPrecacheWebpackPlugin({
      cacheId: 'akto-app',
      filename: 'sw.js',
      staticFileGlobs: ['dist/**/*.{js,css}', '/'],
      minify: true,
      stripPrefix: 'dist/',
      dontCacheBustUrlsMatching: /\.\w{6}\./
    }),
    new webpack.DefinePlugin({
      'process.env': {
        NODE_ENV: '"production"'
      }
    }),
    new UglifyJsPlugin({
          "uglifyOptions":
              {
                compress: {
                  warnings: false
                },
                sourceMap: true,
                output: {
                  ascii_only: true
                }
              }
        }
    ),
    new webpack.LoaderOptionsPlugin({
      minimize: true
    })
  ])
}
