/** 原型只存本机（wx storage）；正式版走自建 /v1/sync，与 App 同一个账本。 */
const KEY = 'xp_txns_v1'

// 内存里的一份账本：读写都走异步 wx.getStorage / setStorage（不阻塞渲染；Dimina 的 *Sync 也不可靠）。
let cache = []

function load(done) {
  wx.getStorage({
    key: KEY,
    success: (res) => { cache = Array.isArray(res.data) ? res.data : [] },
    fail: () => { cache = [] },
    complete: () => done && done(),
  })
}

function all() {
  return cache
}

function add(txn) {
  cache = [txn].concat(cache)
  wx.setStorage({ key: KEY, data: cache })
  return cache
}

function startOfDay(ts) {
  const d = new Date(ts)
  d.setHours(0, 0, 0, 0)
  return d.getTime()
}

/** 备注上方的常用短语：近 90 天，同分类优先，越近权重越高（与 App 的 NotePhrases 同规则）。 */
function phrases(list, category, now, max = 8) {
  const since = now - 90 * 86400000
  const inCat = {}
  const all = {}
  list.forEach((t) => {
    const p = (t.note || '').trim()
    if (!p || p.length > 16 || !/[^\d\s.,]/.test(p) || t.at < since) return
    const w = 1 + Math.exp(-(now - t.at) / 86400000 / 30)
    all[p] = (all[p] || 0) + w
    if (t.category === category) inCat[p] = (inCat[p] || 0) + w
  })
  const order = (m) => Object.keys(m).sort((a, b) => m[b] - m[a] || a.length - b.length || (a < b ? -1 : 1))
  const first = order(inCat)
  return first.concat(order(all).filter((p) => !(p in inCat))).slice(0, max)
}

module.exports = { load, all, add, startOfDay, phrases }
