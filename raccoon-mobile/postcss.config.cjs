module.exports = {
  plugins: {
    'postcss-px-to-viewport-8-plugin': {
      viewportWidth: 375,
      unitPrecision: 5,
      viewportUnit: 'vw',
      selectorBlackList: ['.ignore-vw', /^\.van-/],
      exclude: [/node_modules\/vant/i],
      minPixelValue: 1,
      mediaQuery: true
    }
  }
}
