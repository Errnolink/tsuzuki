package dev.errnolink.tsuzuki.ui.settings

sealed interface ChoicePresentation {
    data object Menu : ChoicePresentation
    data object Existing : ChoicePresentation
}

fun choicePresentation(count: Int, multi: Boolean): ChoicePresentation =
    if (!multi && count in 1..7) ChoicePresentation.Menu else ChoicePresentation.Existing
