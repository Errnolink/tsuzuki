package dev.errnolink.tsuzuki.lab

data class LabRequirement(
    val choice: String,
    val key: String,
    val value: String,
    val reason: String,
)

class LabOption<T : Enum<T>>(
    val key: String,
    val area: String,
    val title: String,
    val summary: String,
    val choices: List<T>,
    val default: T,
    val built: Boolean = false,
    val restart: Boolean = false,
    val requires: List<LabRequirement> = emptyList(),
    val choiceLabel: (T) -> String = { it.name },
    val choiceSummary: (T) -> String? = { null },
) {
    val prefKey: String = "lab_$key"

    fun choiceFor(value: String): T? = choices.firstOrNull { it.name == value }

    fun resolve(stored: String?, enabled: Boolean): T {
        if (!enabled || stored == null) return default
        return choiceFor(stored) ?: default
    }

    fun choiceBlocked(choiceName: String, currentChoiceOf: (String) -> String): LabRequirement? =
        requires.firstOrNull { it.choice == choiceName && currentChoiceOf(it.key) != it.value }

    fun labelOf(choiceName: String): String = choiceLabel(choiceFor(choiceName) ?: default)

    fun summaryOf(choiceName: String): String? = choiceSummary(choiceFor(choiceName) ?: default)
}
