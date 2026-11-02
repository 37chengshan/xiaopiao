const store = require('../../utils/store')
const { yuan, centsOf, press } = require('../../utils/money')

const CATEGORIES = [
  { id: 'meal', label: '吃饭', emoji: '🍚' },
  { id: 'drink', label: '奶茶咖啡', emoji: '🧋' },
  { id: 'transport', label: '出行', emoji: '🚇' },
  { id: 'shopping', label: '购物', emoji: '🛍️' },
  { id: 'fun', label: '娱乐', emoji: '🎮' },
  { id: 'other', label: '其他', emoji: '🧾' },
]
const KEYS = [['7', '8', '9', 'del'], ['4', '5', '6'], ['1', '2', '3']]

function timeLabel(ts) {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function dateLabel(ts) {
  const d = new Date(ts)
  const w = '日一二三四五六'[d.getDay()]
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 周${w}`
}

Page({
  data: {
    nav: null,
    today: '',
    totalYuan: '0.00',
    rows: [],
    categories: CATEGORIES,
    keys: KEYS,
    sheetOpen: false,
    category: 'meal',
    draft: '',
    amountYuan: '0',
    canEnter: false,
    noteFocus: false,
    noteVisited: false,
    note: '',
    phrases: [],
  },

  onLoad() {
    this.setData({ nav: getApp().globalData.nav, today: dateLabel(Date.now()) })
    store.load(() => this.refresh())
  },

  refresh() {
    const from = store.startOfDay(Date.now())
    const list = store.all().filter((t) => t.at >= from)
    const total = list.reduce((s, t) => s + t.cents, 0)
    const byId = Object.fromEntries(CATEGORIES.map((c) => [c.id, c]))
    this.setData({
      totalYuan: yuan(total),
      rows: list.map((t) => ({
        id: t.id,
        emoji: (byId[t.category] || byId.other).emoji,
        title: t.note || (byId[t.category] || byId.other).label,
        time: timeLabel(t.at),
        amount: yuan(t.cents),
      })),
    })
  },

  openSheet() {
    this.setData({ sheetOpen: true, draft: '', amountYuan: '0', canEnter: false, note: '', noteVisited: false, noteFocus: false })
    this.loadPhrases(this.data.category)
  },

  closeSheet() {
    this.setData({ sheetOpen: false, noteFocus: false })
  },

  noop() {},

  pickCategory(e) {
    const id = e.currentTarget.dataset.id
    this.setData({ category: id })
    this.loadPhrases(id)
  },

  loadPhrases(category) {
    this.setData({ phrases: store.phrases(store.all(), category, Date.now()) })
  },

  tapKey(e) {
    const draft = press(this.data.draft, e.currentTarget.dataset.key)
    this.setData({ draft, amountYuan: draft || '0', canEnter: centsOf(draft) > 0 })
  },

  // ↵：第一次跳到备注，备注碰过之后变成「保存」（与 App 一致，tt 2026-11-02）。
  tapEnter() {
    if (!this.data.canEnter) return
    if (this.data.noteVisited) this.save()
    else this.setData({ noteVisited: true, noteFocus: true })
  },

  onNoteFocus() {
    this.setData({ noteFocus: true, noteVisited: true })
  },

  onNoteBlur() {
    this.setData({ noteFocus: false })
  },

  onNoteInput(e) {
    this.setData({ note: e.detail.value.slice(0, 30) })
  },

  pickPhrase(e) {
    this.setData({ note: e.currentTarget.dataset.text })
  },

  save() {
    const cents = centsOf(this.data.draft)
    if (cents <= 0) {
      wx.showToast({ title: '先输入金额', icon: 'none' })
      return
    }
    store.add({ id: Date.now(), cents, category: this.data.category, note: this.data.note.trim(), at: Date.now() })
    this.setData({ sheetOpen: false, noteFocus: false })
    this.refresh()
    wx.showToast({ title: '记好了', icon: 'success' })
  },

  onShareAppMessage() {
    return { title: `今天花了 ¥${this.data.totalYuan}，来看看我的小票`, path: '/pages/today/index' }
  },
})
