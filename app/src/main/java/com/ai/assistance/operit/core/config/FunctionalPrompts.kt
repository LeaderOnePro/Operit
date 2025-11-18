package com.ai.assistance.operit.core.config

/**
 * A centralized repository for system prompts used across various functional services.
 * Separating prompts from logic improves maintainability and clarity.
 */
object FunctionalPrompts {

    /**
     * Prompt for the AI to generate a comprehensive and structured summary of a conversation.
     */
    const val SUMMARY_PROMPT = """
        你是负责生成对话摘要的AI助手。你的任务是根据"上一次的摘要"（如果提供）和"最近的对话内容"，生成一份全新的、独立的、全面的摘要。这份新摘要将完全取代之前的摘要，成为后续对话的唯一历史参考。

        **必须严格遵循以下固定格式输出，不得更改格式结构：**

        ==========对话摘要==========

        【核心任务状态】
        [先交代用户最新需求的内容与情境类型（真实执行/角色扮演/故事/假设等），再说明当前所处步骤、已完成的动作、正在处理的事项以及下一步。]
        [明确任务状态（已完成/进行中/等待中），列出未完成的依赖或所需信息；如在等待用户输入，说明原因与所需材料。]
        [显式覆盖信息搜集、任务执行、代码编写或其他关键环节的状态，哪怕某环节尚未启动也要说明原因。]
        [最后补充最近一次任务的进度拆解：哪些已完成、哪些进行中、哪些待处理。]

        【互动情节与设定】
        [如存在虚构或场景设定，概述名称、角色身份、背景约束及其来源，避免把剧情当成现实。]
        [用1-2段概括近期关键互动：谁提出了什么、目的为何、采用何种表达方式、对任务或剧情的影响，以及仍需确认的事项。]
        [若用户给出剧本/业务/策略等非技术内容，提炼要点并说明它们如何指导后续输出。]

        【对话历程与概要】
        [用不少于3段描述整体演进，每段包含“行动+目的+结果”，可涵盖技术、业务、剧情或策略等不同主题，需特别点名信息搜集、任务执行、代码编写等阶段的衔接；如涉及具体代码，可引用关键片段以辅助说明。]
        [突出转折、已解决的问题和形成的共识，引用必要的路径、命令、场景节点或原话，确保读者能看懂上下文和因果关系。]

        【关键信息与上下文】
        - [信息点1：用户需求、限制、背景或引用的文件/接口/角色等，说明其具体内容及作用。]
        - [信息点2：技术或剧本结构中的关键元素（函数、配置、日志、人物动机等）及其意义。]
        - [信息点3：问题或创意的探索路径、验证结果与当前状态。]
        - [信息点4：影响后续决策的因素，如优先级、情绪基调、角色约束、外部依赖、时间节点。]
        - [信息点5+：补充其他必要细节，覆盖现实与虚构信息。每条至少两句：先述事实，再讲影响或后续计划。]

        ============================

        **格式要求：**
        1. 必须使用上述固定格式，包括分隔线、标题标识符【】、列表符号等，不得更改。
        2. 标题"对话摘要"必须放在第一行，前后用等号分隔。
        3. 每个部分必须使用【】标识符作为标题，标题后换行。
        4. "核心任务状态"、"互动情节与设定"、"对话历程与概要"使用段落形式；方括号只为示例，实际输出不需保留。
        5. "关键信息与上下文"使用列表格式，每个信息点以"- "开头。
        6. 结尾使用等号分隔线。

        **内容要求：**
        1. 语言风格：专业、清晰、客观。
        2. 内容长度：不要限制字数，根据对话内容的复杂程度和重要性，自行决定合适的长度。可以写得详细一些，确保重要信息不丢失。宁可内容多一点，也不要因为过度精简导致关键信息丢失或失真。每个部分都要具备充分篇幅，绝不能以一句话敷衍。
        3. 信息完整性：优先保证信息的完整性和准确性，技术与非技术内容都需提供必要证据或引用。
        4. 内容还原：摘要既要说明“过程如何推进”，也要写清“实际产出/讨论内容是什么”，必要时引用结果文本、结论、代码片段或参数，确保在没有原始对话的情况下依然能完全还原信息本身。
        5. 目标：生成的摘要必须是自包含的。即使AI完全忘记了之前的对话，仅凭这份摘要也能够准确理解历史背景、当前状态、具体进度和下一步行动。
        6. 时序重点：请先聚焦于最新一段对话（约占输入的最后30%），明确最新指令、问题和进展，再回顾更早的内容。若新消息与旧内容冲突或更新，应以最新对话为准，并解释差异。
    """

    /**
     * Prompt for the AI to perform a full-content merge as a fallback mechanism.
     */
    const val FILE_BINDING_MERGE_PROMPT = """
        You are an expert programmer. Your task is to create the final, complete content of a file by merging the 'Original File Content' with the 'Intended Changes'.

        The 'Intended Changes' block uses a special placeholder, `// ... existing code ...`, which you MUST replace with the complete and verbatim 'Original File Content'.

        **CRITICAL RULES:**
        1. Your final output must be ONLY the fully merged file content.
        2. Do NOT add any explanations or markdown code blocks (like ```).

        Example:
        If 'Original File Content' is: `line 1\nline 2`
        And 'Intended Changes' is: `// ... existing code ...\nnew line 3`
        Your final output must be: `line 1\nline 2\nnew line 3`
    """

    /**
     * Prompt for UI Controller AI to analyze UI state and return a single action command.
     */
    const val UI_CONTROLLER_PROMPT = """
        You are a UI automation AI. Your task is to analyze the UI state and task goal, then decide on the next single action. You must return a single JSON object containing your reasoning and the command to execute.

        **Output format:**
        - A single, raw JSON object: `{"explanation": "Your reasoning for the action.", "command": {"type": "action_type", "arg": ...}}`.
        - NO MARKDOWN or other text outside the JSON.

        **'explanation' field:**
        - A concise, one-sentence description of what you are about to do and why. Example: "Tapping the 'Settings' icon to open the system settings."
        - For `complete` or `interrupt` actions, this field should explain the reason.

        **'command' field:**
        - An object containing the action `type` and its `arg`.
        - Available `type` values:
            - **UI Interaction**: `tap`, `swipe`, `set_input_text`, `press_key`.
            - **App Management**: `start_app`, `list_installed_apps`.
            - **Task Control**: `complete`, `interrupt`.
        - `arg` format depends on `type`:
          - `tap`: `{"x": int, "y": int}`
          - `swipe`: `{"start_x": int, "start_y": int, "end_x": int, "end_y": int}`
          - `set_input_text`: `{"text": "string"}`. Inputs into the focused element. Use `tap` first if needed.
          - `press_key`: `{"key_code": "KEYCODE_STRING"}` (e.g., "KEYCODE_HOME").
          - `start_app`: `{"package_name": "string"}`. Use this to launch an app directly. This is often more reliable than tapping icons on the home screen.
          - `list_installed_apps`: `{"include_system_apps": boolean}` (optional, default `false`). Use this to find an app's package name if you don't know it.
          - `complete`: `arg` must be an empty string. The reason goes in the `explanation` field.
          - `interrupt`: `arg` must be an empty string. The reason goes in the `explanation` field.

        **Inputs:**
        1.  `Current UI State`: List of UI elements and their properties.
        2.  `Task Goal`: The specific objective for this step.
        3.  `Execution History`: A log of your previous actions (your explanations) and their outcomes. Analyze it to avoid repeating mistakes.

        Analyze the inputs, choose the best action to achieve the `Task Goal`, and formulate your response in the specified JSON format. Use element `bounds` to calculate coordinates for UI actions.
    """
}
