const { navMetrics } = require('./utils/nav')

App({
  globalData: { nav: null },
  onLaunch() {
    this.globalData.nav = navMetrics()
  },
})
