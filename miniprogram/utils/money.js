/** 金额一律用整数「分」，和 App 的 Cents 一致；只在展示时转成元。 */
function yuan(cents) {
  const sign = cents < 0 ? '-' : ''
  const abs = Math.abs(cents)
  const whole = Math.floor(abs / 100)
  const frac = String(abs % 100).padStart(2, '0')
  return `${sign}${whole.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${frac}`
}

/** 键盘输入的草稿（如 "12.5"）→ 分；非法或 0 返回 0。 */
function centsOf(draft) {
  if (!/^\d{0,7}(\.\d{0,2})?$/.test(draft) || draft === '' || draft === '.') return 0
  const [w, f = ''] = draft.split('.')
  return Number(w || '0') * 100 + Number((f + '00').slice(0, 2))
}

/** 按一个键后的新草稿：最多两位小数、一个小数点、7 位整数。 */
function press(draft, key) {
  if (key === 'del') return draft.slice(0, -1)
  if (key === '.') {
    if (draft.includes('.')) return draft
    return (draft || '0') + '.'
  }
  const next = draft === '0' ? key : draft + key
  return /^\d{0,7}(\.\d{0,2})?$/.test(next) ? next : draft
}

module.exports = { yuan, centsOf, press }
