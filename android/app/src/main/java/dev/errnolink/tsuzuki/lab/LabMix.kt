package dev.errnolink.tsuzuki.lab

object LabMix {
    const val VERSION = "v1"
    const val MIX_FILE_NAME = "design-lab.txt"

    fun encode(values: Map<String, String>): String {
        val builder = StringBuilder(VERSION)
        for (option in DesignLab.all) {
            val value = values[option.key] ?: continue
            builder.append(';').append(option.key).append('=').append(value)
        }
        return builder.toString()
    }

    data class Result(
        val accepted: Map<String, String>,
        val ignoredKeys: List<String>,
        val ignoredValues: List<String>,
        val malformed: Boolean,
    )

    fun decode(text: String): Result {
        val segments = text.trim().split(';')
        if (segments.firstOrNull() != VERSION) {
            return Result(emptyMap(), emptyList(), emptyList(), malformed = true)
        }
        val accepted = LinkedHashMap<String, String>()
        val ignoredKeys = ArrayList<String>()
        val ignoredValues = ArrayList<String>()
        for (segment in segments.drop(1)) {
            if (segment.isEmpty()) continue
            val separator = segment.indexOf('=')
            if (separator <= 0) {
                return Result(emptyMap(), emptyList(), emptyList(), malformed = true)
            }
            val key = segment.substring(0, separator)
            val value = segment.substring(separator + 1)
            val option = DesignLab.option(key)
            if (option == null) {
                ignoredKeys.add(key)
                continue
            }
            if (option.choiceFor(value) == null) {
                ignoredValues.add(value)
                continue
            }
            accepted[key] = value
        }
        return Result(accepted, ignoredKeys, ignoredValues, malformed = false)
    }
}
