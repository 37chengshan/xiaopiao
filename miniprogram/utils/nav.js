/**
 * 自定义导航栏的尺寸，全部从系统胶囊（右上角「··· / ⊙」）推出来。
 * 胶囊是微信自带、不能去掉也不能盖住的：标题靠左，右侧留出 menu.left 之后的整段，不放任何自己的按钮。
 */
function navMetrics() {
  const sys = wx.getSystemInfoSync()
  const statusBar = sys.statusBarHeight || 20
  let menu = null
  try {
    menu = wx.getMenuButtonBoundingClientRect()
  } catch (e) {
    menu = null
  }
  // 拿不到胶囊（老基础库 / 模拟环境）时按微信默认胶囊 87×32、右边距 7 估算。
  if (!menu || !menu.width) {
    menu = { top: statusBar + 4, bottom: statusBar + 36, left: sys.windowWidth - 94, height: 32, width: 87 }
  }
  const gap = menu.top - statusBar
  return {
    statusBar,
    barHeight: menu.height + gap * 2,
    total: statusBar + menu.height + gap * 2,
    // 标题区右边界：胶囊左侧再留 8px。
    safeRight: sys.windowWidth - menu.left + 8,
  }
}

module.exports = { navMetrics }
