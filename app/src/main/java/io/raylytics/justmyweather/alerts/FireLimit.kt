package io.raylytics.justmyweather.alerts

/**
 * How many times a rule may fire before it goes quiet: once, up to a small
 * number, or every time (the default, and the only behaviour before this
 * existed). "Fire" means one notification — a rule entering its fired state —
 * so an unlimited rule still pings once per onset, not once per poll.
 *
 * A reached limit switches the rule off rather than hiding it, so the Alerts
 * list shows what happened and the usual toggle re-arms it with a fresh count.
 */
data class FireLimit private constructor(
    /** Null means unlimited; otherwise 1..[MAX]. */
    val times: Int?,
) {
    val isUnlimited: Boolean get() = times == null

    /** The chip label in the rule builder and the start of the rule caption. */
    val label: String
        get() =
            when (times) {
                null -> "Every time"
                1 -> "Once"
                else -> "Up to $times times"
            }

    /** What the codec writes: 0 for unlimited, since JSON has no enum and the
     * absent key on rules saved before limits existed must read as unlimited. */
    val stored: Int get() = times ?: 0

    companion object {
        /** Two digits is plenty for a personal alert; anything larger is
         * "every time" in all but name. */
        const val MAX = 99

        val UNLIMITED = FireLimit(null)
        val ONCE = FireLimit(1)

        fun times(n: Int): FireLimit {
            require(n in 1..MAX) { "a fire limit must be 1..$MAX, got $n" }
            return FireLimit(n)
        }

        /** Lenient inverse of [stored]: anything outside 1..[MAX] (including the
         * 0 written for unlimited, and any value a future build might invent)
         * reads as unlimited rather than dropping the rule. */
        fun fromStored(stored: Int): FireLimit = if (stored in 1..MAX) FireLimit(stored) else UNLIMITED
    }
}
