package com.nextnotes.ai

/** A one-tap question about the attached note content. */
data class QuickAction(val label: String, val prompt: String)

object Prompts {
    /** Kept short: it rides along with every request. */
    val system = """
        你是 NextNotes 手写笔记应用里的学习助手。用户会发来笔记页面或圈选区域的截图（多为手写，可能有涂改），并提出问题。
        - 先认真辨认图中的手写内容；看不清的地方直接说明，不要臆测。
        - 用简体中文回答，结构清晰、简洁。
        - 数学公式用 LaTeX：行内用 ${'$'}...${'$'}，独立公式用 ${'$'}${'$'}...${'$'}${'$'}。
        - 检查解题过程时，按步骤逐条指出对错；有错就说明错在哪、为什么、正确做法是什么。
    """.trimIndent()

    val quickActions = listOf(
        QuickAction("讲解", "请讲解图中的内容：它在说什么，涉及哪些知识点。"),
        QuickAction("检查过程", "请逐步检查图中的解题过程：每一步是否正确？如有错误，指出是哪一步、错在哪里，并给出正确的推导。最后给出结论。"),
        QuickAction("只给提示", "我想自己解这道题。请不要直接给出答案，只给我下一步的思路提示。"),
        QuickAction("解答", "请完整解答图中的题目，给出详细步骤。"),
        QuickAction("总结", "请把图中的笔记整理成要点总结。"),
    )

    /** The note snapshot sent without a typed question still needs one. */
    const val defaultQuestion = "请看这部分笔记，帮我解答或讲解其中的内容。"
}
