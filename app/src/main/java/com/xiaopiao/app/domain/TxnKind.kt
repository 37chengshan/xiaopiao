package com.xiaopiao.app.domain

/** 预算「计入」开关认的两类钱。只看 merchant / note 里的字样，不新增存储字段。 */
enum class TxnKind(val keyword: String) {
    Transfer("转账"),
    RedPacket("红包"),
}

object TxnKinds {
    fun of(txn: Txn): TxnKind? {
        val text = txn.merchant + txn.note
        return when {
            text.contains(TxnKind.Transfer.keyword) -> TxnKind.Transfer
            text.contains(TxnKind.RedPacket.keyword) -> TxnKind.RedPacket
            else -> null
        }
    }

    /** 详情页只给转账、红包和收到的钱显示开关；其它支出不需要。 */
    fun showsCountSwitch(txn: Txn): Boolean = of(txn) != null || txn.direction == Direction.IN
}
