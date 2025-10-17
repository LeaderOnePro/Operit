# API 文档: `query.d.ts`

本文档详细介绍了 `query.d.ts` 文件中定义的 API，该 API 提供了与内部记忆库进行交互的功能。

## 概述

所有知识查询相关的功能都封装在全局的 `Tools.Query` 命名空间下。这个模块目前提供了一个核心功能，用于从项目中配置的记忆库或向量数据库中检索信息。

---

## `Tools.Query` 命名空间详解

### `knowledge(query: string): Promise<string>`

向内部记忆库发送一个自然语言查询，并获取最相关的结果。

-   **`query`**: 你希望查询的问题或关键词，以字符串形式提供。
-   **返回值**: 一个 `Promise`，成功时解析为一个字符串，其中包含了从记忆库中检索到的相关信息。返回内容的格式和详细程度取决于记忆库的实现。

**示例:**

```typescript
async function queryKnowledgeBase() {
    const question = "如何设置开发环境？";
    try {
        const answer = await Tools.Query.knowledge(question);
        
        console.log(`关于 "${question}" 的查询结果:\n${answer}`);

        complete({
            success: true,
            message: "记忆库查询成功。",
            data: answer
        });
    } catch (error) {
        complete({
            success: false,
            message: `记忆库查询失败: ${error.message}`
        });
    }
}
``` 