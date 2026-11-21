package com.nextnotes.ai

/**
 * A minimal Server-Sent Events reader: feed it lines as they arrive and it hands each complete
 * event (its `event:` name, if any, and its joined `data:` lines) to [onEvent].
 */
class SseReader(private val onEvent: (event: String?, data: String) -> Unit) {
    private var event: String? = null
    private val data = StringBuilder()
    private var hasData = false

    fun line(raw: String) {
        val line = raw.removeSuffix("\r")
        when {
            line.isEmpty() -> dispatch()
            line.startsWith(":") -> Unit // comment / keep-alive
            else -> {
                val colon = line.indexOf(':')
                val field = if (colon < 0) line else line.substring(0, colon)
                var value = if (colon < 0) "" else line.substring(colon + 1)
                if (value.startsWith(" ")) value = value.substring(1)
                when (field) {
                    "event" -> event = value
                    "data" -> {
                        if (hasData) data.append('\n')
                        data.append(value)
                        hasData = true
                    }
                }
            }
        }
    }

    /** End of stream: a last event without its trailing blank line still counts. */
    fun finish() = dispatch()

    private fun dispatch() {
        if (hasData) onEvent(event, data.toString())
        event = null
        data.setLength(0)
        hasData = false
    }
}
