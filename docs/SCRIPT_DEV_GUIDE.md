# 脚本开发指南

## 1. 简介

本文档旨在为开发者提供关于如何编写、构建和维护自动化脚本的全面指南。**这些脚本旨在作为强大的工具，被导入到 Operit AI 智能助手中，并由 AI 根据用户指令进行调用，从而极大地扩展应用的功能边界。** 这些脚本基于一个强大的框架，提供了一系列用于设备控制、UI自动化、网络请求和文件操作的工具。

脚本主要使用 **TypeScript** 编写，以利用其强大的类型系统，但也可以使用原生 JavaScript (ES6+)。

## 2. 快速上手

我们为开发者提供了两种不同的开发路径，你可以根据自己的需求选择最合适的一种。

*   **路径 A：在 Operit 项目中直接开发 (推荐)**: 这是最简单快捷的方式，无需手动配置环境。适合想要快速编写脚本并贡献给主项目的开发者。
*   **路径 B：创建独立的脚本项目 (高级)**: 如果你想创建和维护自己的脚本仓库，或者将脚本作为独立产品发布，可以选择此路径。这需要你手动完成项目的搭建。

---

### 路径 A: 在 Operit 项目中直接开发 (5分钟快速入门)

这是最推荐的入门方式。Operit 项目本身就是一个功能完备的开发环境，让你免于手动配置。

**步骤 1: 克隆项目并安装依赖**

```bash
# 克隆项目仓库
git clone https://github.com/AAswordman/Operit.git
cd Operit

# 安装项目依赖 (主要是TypeScript编译器)
npm install
```

**步骤 2: 创建你的第一个脚本**

所有的脚本都存放在 `examples/` 目录下。请在该目录中创建一个新文件，例如 `my_first_script.ts`，然后将下面的代码复制进去。

```typescript
/*
METADATA
{
    "name": "MyFirstScript",
    "description": "我的第一个脚本，用于演示。",
    "category": "TUTORIAL",
    "tools": [
        {
            "name": "hello_world",
            "description": "向世界问好。",
            "parameters": [
                {
                    "name": "name",
                    "description": "要问好的人名",
                    "type": "string",
                    "required": true
                }
            ]
        }
    ]
}
*/

// 引用核心类型，这会给你带来代码自动补全的奇效
/// <reference path="./types/index.d.ts" />

// 这是一个标准的脚本结构，建议你直接复用
const MyFirstScript = (function () {
    // 包装函数，统一处理成功/失败的返回
    async function wrap(func: (params: any) => Promise<any>, params: any) {
        try {
            const result = await func(params);
            complete(result); // 必须调用 complete() 来结束脚本
        } catch (error) {
            complete({ success: false, message: `执行失败: ${error.message}` });
        }
    }

    // 这是你工具的具体实现
    async function hello_world(params: { name: string }): Promise<any> {
        const message = `你好, ${params.name}! 欢迎来到 Operit 脚本世界。`;
        await Tools.System.sleep(500); // 调用内置API
        return { success: true, message: message };
    }

    // 导出你的工具，使其可以被调用
    return {
        hello_world: (params: any) => wrap(hello_world, params),
    };
})();

// 将工具函数暴露给脚本执行引擎
exports.hello_world = MyFirstScript.hello_world;
```

**步骤 3: 编译脚本**

TypeScript (`.ts`) 文件需要被编译成 JavaScript (`.js`) 才能执行。`tsc` 命令会自动读取 `tsconfig.json` 文件并根据其配置来编译。

```bash
# 进入 examples 目录
cd examples

# 编译目录下的所有 TypeScript 脚本
npx tsc
```

执行成功后，你会看到 `my_first_script.ts` 旁边生成了一个 `my_first_script.js` 文件。

**步骤 4: 在设备上运行脚本**

确保你已连接安卓设备并开启了USB调试。然后返回项目根目录，使用我们提供的工具来执行脚本。

```bash
# 回到项目根目录
cd ..

# 执行脚本的 `hello_world` 函数
# Windows:
tools\\execute_js.bat examples\\my_first_script.js hello_world "{\\"name\\":\\"世界\\"}"
# Linux / macOS:
./tools/execute_js.sh examples/my_first_script.js hello_world '{"name":"世界"}'
```

**步骤 5: 查看结果**

脚本会自动拉起设备的日志。如果一切顺利，你会在终端看到包含 `"你好, 世界!..."` 的成功信息。

**恭喜你！**

你已经完成了第一个脚本的开发和运行。现在你已经掌握了基础，可以继续深入探索 **[第 3 章：核心概念](#3-核心概念)** 来学习 `METADATA` 的详细配置和 `Tools` API 的更多用法，或者直接跳到 **[第 6 章：UI自动化详解](#6-ui自动化详解)** 编写更强大的自动化脚本。

---

### 路径 B: 创建独立的脚本项目

如果你希望独立管理你的脚本，可以按照以下步骤从零开始搭建一个开发环境。

#### 2.1. 初始化项目与依赖 (`package.json`)

`package.json` 文件是 Node.js 项目的清单，用于管理项目的元数据和依赖项。

**步骤 1: 创建 `package.json`**

在你的空项目文件夹中，创建一个名为 `package.json` 的文件，并填入以下基础内容：

```json
{
    "name": "my-script-project",
    "version": "1.0.0",
    "description": "My new script project.",
    "scripts": {
        "build": "tsc"
    },
    "devDependencies": {
        "@types/node": "^22.0.0",
        "typescript": "^5.4.5"
    }
}
```
*注意: 我们推荐使用较新版本的 `typescript` 和 `@types/node`，你可以根据需要调整版本号。*

**步骤 2: 安装依赖**

打开终端，在项目根目录下运行以下命令来安装开发依赖：

```bash
npm install
```

此命令会根据 `package.json` 中的 `devDependencies` 下载 `typescript` 和 Node.js 的类型定义。

#### 2.2. 配置 TypeScript (`tsconfig.json`)

`tsconfig.json` 文件用于指定 TypeScript 编译器的选项，告诉它如何将 `.ts` 文件编译成 `.js` 文件。

**步骤: 创建 `tsconfig.json`**

在项目根目录创建 `tsconfig.json` 文件，并复制以下推荐配置。这个配置与本项目使用的标准配置 (`examples/tsconfig.json`) 保持一致，确保了兼容性。

```json
{
  "compilerOptions": {
    "target": "es2017",
    "module": "commonjs",
    "lib": [
      "es2017",
      "dom"
    ],
    "declaration": false,
    "strict": false,
    "noImplicitAny": false,
    "strictNullChecks": true,
    "noImplicitThis": true,
    "alwaysStrict": false,
    "noUnusedLocals": false,
    "noUnusedParameters": false,
    "noImplicitReturns": true,
    "moduleResolution": "node",
    "allowSyntheticDefaultImports": true,
    "esModuleInterop": true,
    "experimentalDecorators": true,
    "emitDecoratorMetadata": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "typeRoots": [
      "./types"
    ]
  },
  "include": [
    "**/*.ts"
  ],
  "exclude": [
    "node_modules"
  ]
}
```

**关键配置解释:**
-   `"target": "es2017"`: 将代码编译为 ES2017 版本的 JavaScript。
-   `"module": "commonjs"`: 使用 CommonJS 模块系统，这是脚本执行环境所要求的。
-   `"typeRoots": ["./types"]`: 指定了类型定义文件 (`.d.ts`) 的存放目录。你需要手动在项目中创建一个 `types` 文件夹，并将平台提供的核心类型定义文件 (`index.d.ts`, `files.d.ts` 等) 放进去，这样才能获得 `Tools` 等全局对象的智能提示。
-   `"include": ["**/*.ts"]`: 告诉编译器编译当前目录下所有的 `.ts` 文件。

完成以上步骤后，你的项目就搭建好了。现在你可以继续阅读后续章节，了解项目的具体结构和脚本的编写方法。

#### 2.3. 复制平台核心文件

为了让你的脚本能与平台交互，并能在设备上执行，你需要从本开发指南所在的源项目（即 `assistance` 项目）中复制一些核心文件到你的新项目里。

**需要复制的内容：**

1.  **类型定义文件**: 将源项目中的 `examples/types/` 整个文件夹复制到你的新项目根目录，并确保文件夹名称就是 `types`。这与 `tsconfig.json` 中的 `"typeRoots": ["./types"]` 配置相对应。这些文件定义了 `Tools` 等全局对象的类型，是获得代码智能提示的关键。
2.  **执行工具**: 将源项目根目录下的 `tools/` 整个文件夹复制到你的新项目根目录。这些脚本（如 `execute_js.bat`）是用来在设备上运行和测试你的代码的。

**复制后的项目结构**

完成以上所有步骤后，你的新项目文件夹看起来应该是这样的：

```plaintext
my-script-project/
├── node_modules/
├── tools/
│   ├── execute_js.bat
│   └── execute_js.sh
├── types/
│   ├── core.d.ts
│   ├── files.d.ts
│   ├── index.d.ts
│   ├── network.d.ts
│   ├── system.d.ts
│   ├── ui.d.ts
│   └── ... (其他核心类型定义文件)
├── my_first_script.ts  // 这是你将要创建的脚本文件
├── package.json
└── tsconfig.json
```

现在，你的开发环境已经完全准备就绪。

## 3. 核心概念

在开始编写脚本之前，理解以下几个核心概念非常重要：

### 3.1. 脚本元数据 (METADATA)

每个脚本文件的开头都必须包含一个 `/* METADATA ... */` 注释块。这个块定义了脚本的名称、描述、分类以及最重要的——它所提供的工具。**这块元数据是 Operit AI 理解并调用你所编写功能的唯一途径。AI 会解析 `METADATA` 中的信息，将其作为可用的“工具”呈现给大语言模型（LLM），从而实现通过自然语言指令来执行复杂脚本的能力。**

**示例：** (`examples/automatic_bilibili_assistant.ts`)

```typescript
/*
METADATA
{
    "name": "Automatic_bilibili_assistant",
    "description": "高级B站智能助手，通过UI自动化技术实现B站应用交互...",
    "category": "UI_AUTOMATION",
    "tools": [
        {
            "name": "search_video",
            "description": "在B站搜索视频内容",
            "parameters": [
                {
                    "name": "keyword",
                    "description": "搜索关键词",
                    "type": "string",
                    "required": true
                },
                // ... more parameters
            ]
        },
        // ... more tools
    ]
}
*/
```

-   `name`: 脚本的唯一标识符。
-   `description`: 对脚本功能的详细描述。
-   `category`: 脚本分类，例如 `UI_AUTOMATION`, `NETWORK`, `DAILY_LIFE`。
-   `tools`: 一个数组，定义了该脚本暴露给外部调用的所有工具（函数）。
    -   `name`: 工具的函数名。
    -   `description`: 工具功能的描述。
    -   `parameters`: 工具接受的参数列表，每个参数都应定义 `name`, `description`, `type`, 和 `required`。

### 3.2. 脚本执行与结束

脚本中的每个工具函数都是异步的。当工具函数完成其任务后，**必须**调用全局的 `complete()` 函数来结束执行并返回结果。

`complete(result: object)`:
-   `result`: 一个包含至少 `success` (boolean) 和 `message` (string) 字段的对象。可以附加任何其他需要返回的数据。

**示例：**
```typescript
async function get_current_date(params: {}) {
    try {
        const date = new Date().toISOString();
        // 成功时返回
        complete({
            success: true,
            message: "成功获取当前日期",
            data: date
        });
    } catch (error) {
        // 失败时返回
        complete({
            success: false,
            message: `获取日期失败: ${error.message}`,
            error_stack: error.stack
        });
    }
}
```

为了简化代码，大多数脚本都使用了一个包装函数（例如 `wrap` 或 `wrapToolExecution`）来统一处理 `try...catch` 和 `complete()` 的调用。

### 3.3. 使用内置工具 (Tools)

平台提供了一个全局的 `Tools` 对象，它包含了所有与底层系统交互的API。这些API被分类到不同的命名空间下:

-   `Tools.System`: 系统级操作，如 `sleep()`, `startApp()`, `stopApp()`。
-   `Tools.UI`: UI自动化操作，如 `getPageInfo()`, `pressKey()`, `swipe()`, `setText()`。
-   `Tools.Files`: 文件系统操作，如 `read()`, `write()`, `list()`。
-   `Tools.Network`: 网络请求，如 `httpGet()`, `httpPost()`。
-   `UINode`: 用于表示和操作UI元素的类。

所有这些工具函数都是**异步**的，调用时必须使用 `await`。

**示例：**
```typescript
// 等待3秒
await Tools.System.sleep(3000);

// 获取当前页面信息
const pageInfo = await Tools.UI.getPageInfo();

// 在屏幕上滑动
await Tools.UI.swipe(540, 1800, 540, 900);
```

## 4. 编写第一个脚本 (TypeScript)

推荐使用 TypeScript 来编写脚本，这样可以充分利用 `types/` 目录中提供的类型定义，获得更好的开发体验。

### 步骤 1: 创建 `.ts` 文件

在你选择的脚本目录下创建一个新的 `.ts` 文件，例如 `my_new_script.ts`。

### 步骤 2: 添加元数据

在文件顶部添加 `METADATA` 块，定义你的脚本和工具。

```typescript
/*
METADATA
{
    "name": "MyNewScript",
    "description": "一个用于演示脚本开发的新脚本。",
    "category": "TUTORIAL",
    "tools": [
        {
            "name": "hello_world",
            "description": "向指定的人问好。",
            "parameters": [
                {
                    "name": "name",
                    "description": "要问好的人名",
                    "type": "string",
                    "required": true
                }
            ]
        }
    ]
}
*/
```

### 步骤 3: 编写主体逻辑

使用一个立即执行函数表达式 (IIFE) 来封装脚本逻辑，避免污染全局作用域。

```typescript
// 引用核心类型，以获得代码提示
/// <reference path="./types/index.d.ts" />

const MyNewScript = (function () {
    // 辅助函数，用于统一返回结果
    async function wrapToolExecution(func: (params: any) => Promise<any>, params: any) {
        try {
            const result = await func(params);
            complete(result);
        } catch (error) {
            console.error(`工具 ${func.name} 执行失败`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }

    // 实现 `hello_world` 工具
    async function hello_world(params: { name: string }): Promise<any> {
        const { name } = params;
        
        // 使用内置工具
        await Tools.System.sleep(500);
        
        const message = `你好, ${name}! 欢迎使用脚本。`;
        
        // 返回成功结果
        return { success: true, message: message };
    }

    // 导出工具
    return {
        hello_world: (params: any) => wrapToolExecution(hello_world, params),
    };
})();

// 导出模块，使其可以被系统加载
exports.hello_world = MyNewScript.hello_world;
```

### 步骤 4: 使用类型

-   在文件顶部添加 `/// <reference path="./types/index.d.ts" />` 可以让 TypeScript 编译器和你的IDE（如 VS Code）找到全局的类型定义。
-   `types/` 目录下的 `.d.ts` 文件详细定义了所有可用工具的签名和返回类型。例如，`types/system.d.ts` 中定义了 `Tools.System.sleep` 的参数和返回值。

## 5. 深入学习：示例脚本解析

当你掌握了基础脚本的写法后，最好的学习方式就是阅读现有的优秀代码。`examples/` 目录下包含了许多功能各异的脚本，我们精选了以下几个作为推荐的进阶学习材料：

### 5.1. 终极入门教程: `examples/quick_start.ts`

这个脚本是一部从零开始的、带有超详细注释的教科书。它不仅仅是一个工具，更是一份动态的开发文档。

*   **学习重点**:
    *   **语法基础**: 详细解释了变量、数据类型、`async/await` 和 `try/catch` 错误处理。
    *   **设计模式演进**: 从一个简单的全局函数开始，逐步重构为使用 `IIFE` 模式隔离作用域，最终演进到我们强烈推荐的 `Wrapper` 模式，让你深刻理解为什么我们的代码要这样组织。
    *   **`Tools` API 调用**: 演示了如何调用 `Tools.System` 和 `Tools.Files` 来与系统交互。
*   **建议**: 如果你对 TypeScript/JavaScript 不熟悉，或者对 `IIFE` 和 `Wrapper` 模式感到困惑，请务必完整阅读一遍此文件。

### 5.2. 网络请求与并发: `examples/various_search.ts`

这个脚本实现了一个实用的多平台搜索引擎聚合工具，是学习网络操作的最佳范例。

*   **学习重点**:
    *   **网络请求**: 演示了如何使用 `Tools.Net.visit` 来访问网页并获取内容。
    *   **多工具聚合**: `combined_search` 函数展示了如何在一个工具内部调用其他多个工具的逻辑，并使用 `Promise.all` 来并发执行它们，极大地提升了效率。
    *   **代码复用**: `performSearch` 作为一个内部辅助函数，被多个导出的工具复用，展示了良好的代码组织。

### 5.3. 纯粹的工具集: `examples/time.ts`

这是一个非常简洁的工具包，提供了多种时间格式化功能。

*   **学习重点**:
    *   **IIFE 的标准用法**: 完美展示了如何使用 `IIFE` 模式来封装一组相关的、无副作用的纯函数。
    *   **多函数导出**: 演示了如何在一个文件中定义并导出多个小而美的功能函数。
    *   **无外部依赖**: 这个脚本完全不依赖 `Tools` API，是一个纯粹的逻辑计算示例。

### 5.4. “元数据”的高级技巧: `examples/various_output.ts`

这个脚本看起来很简单，但它展示了一种与AI交互的特殊技巧。

*   **学习重点**:
    *   **利用描述(Description)**: `output_image` 工具的 `description` 字段并非简单描述功能，而是直接给出了一段提示(prompt)，指导AI如何通过输出特定格式的Markdown文本来展示图片。
    *   **另类工具**: 这表明了 `METADATA` 不仅可以用来定义可执行的函数，还可以作为一种向AI传递“指令”或“知识”的灵活机制。

通过研究以上这些脚本，你可以更快地掌握脚本开发的核心思想和最佳实践。

## 6. UI自动化详解

UI自动化是许多脚本的核心。

### `UINode` 对象

`Tools.UI.getPageInfo()` 返回的页面结构是一个 `UINode` 对象树。每个 `UINode` 代表一个屏幕上的UI元素，你可以通过它来：
-   查找子元素 (`findById`, `findByText`, `findByClass`, `findAllBy...`)
-   获取元素属性 (`text`, `contentDesc`, `bounds`, `resourceId`)
-   执行操作 (`click()`)

### 查找元素

查找元素是自动化的第一步。

```typescript
// 获取当前页面的根节点
const page = await UINode.getCurrentPage();

// 1. 通过资源ID查找 (最稳定)
const searchBox = page.findById('com.example:id/search_box');

// 2. 通过文本内容查找
const loginButton = page.findByText("登录");

// 3. 通过类名查找
const allTextViews = page.findAllByClass('TextView');

// 4. 通过内容描述 (contentDescription) 查找
const backButton = page.findByContentDesc("返回");
```

### 与元素交互

找到元素后，可以对其进行操作。

```typescript
if (loginButton) {
    await loginButton.click();
    await Tools.System.sleep(2000); // 等待页面跳转
}
```

## 7. 调试

-   使用 `console.log()`、`console.error()` 等函数输出日志。日志信息可以在执行环境中查看。
-   将复杂的逻辑拆分成小函数，并为每个函数添加清晰的日志输出。
-   在执行UI操作后，加入适当的 `Tools.System.sleep()` 来等待UI更新，避免操作过快导致失败。

## 8. 编译

TypeScript 脚本 (`.ts`) 需要被编译成 JavaScript (`.js`)才能被执行。项目已配置好 `tsconfig.json`，通常可以使用 `tsc` 命令来编译所有脚本。 

## 9. 在设备上运行和测试脚本

当你编写完脚本并将其编译成 JavaScript 后，可以使用 `tools/` 目录下的辅助脚本通过 ADB (Android Debug Bridge) 在连接的安卓设备上运行它。

### 9.1. 前提条件

- **Android SDK (ADB)**: 确保你已经安装了 Android SDK，并且 `adb` 命令在你的系统路径中可用。
- **安卓设备**: 连接一台开启了“USB调试”功能的安卓设备，并已授权电脑进行调试。
- **Operit 应用程序**: 确保 `com.ai.assistance.operit` 应用程序已经安装并在目标设备上运行。脚本的执行依赖于应用内的 `ScriptExecutionReceiver` 来接收和处理来自 ADB 的命令。

### 9.2. 执行脚本函数

`tools` 目录下提供了 `execute_js.bat` (Windows) 和 `execute_js.sh` (Linux/macOS) 脚本来简化执行流程。

**使用方法 (以 Windows 为例):**

打开命令行工具，执行以下命令：

```cmd
tools\\execute_js.bat <JS文件路径> <要调用的函数名> [JSON格式的参数]
```

**示例：**

假设我们要执行 `my_new_script.js` 中的 `hello_world` 函数，并传入参数 `{ "name": "世界" }`：

```cmd
tools\\execute_js.bat examples\\my_new_script.js hello_world "{\\"name\\":\\"世界\\"}"
```

- 如果连接了多台设备，脚本会提示你选择要操作的设备。
- 注意：在 Windows `cmd` 中，JSON 字符串中的双引号需要使用 `\` 来转义。

### 9.3. 查看输出和调试

脚本执行后，`console.log` 的输出以及 `complete()` 函数返回的结果会打印到设备的日志中。你可以使用 `adb logcat` 来查看这些信息。

为了方便过滤，执行脚本会自动开始监听 `JsEngine` 标签的日志。

```bash
# 脚本会自动执行以下命令来捕获日志
adb logcat -s JsEngine:*
```

你会在终端看到类似下面格式的输出，其中包含了脚本的执行结果：

```
I/JsEngine: Script execution completed.
    Result: {"success":true,"message":"你好, 世界! 欢迎使用脚本。"}
```

这样就完成了一个从编写到设备上测试的完整开发循环。 

### 9.4. VS Code 集成 (推荐)

为了进一步简化开发流程，项目预置了 VS Code 的启动配置，让你可以直接在编辑器内一键运行和测试脚本。

**如何使用：**

1.  在 VS Code 中打开你要测试的脚本文件（`.ts` 或 `.js`）。
2.  切换到“运行和调试”视图 (快捷键 `Ctrl+Shift+D`)。
3.  在顶部的下拉菜单中，你会看到两个可用的配置：
    -   **`在Android设备上运行TS (编译+运行)`**: **推荐用法**。当你正在编辑一个 `.ts` 文件时，选择此项。它会自动编译你的代码，然后提示你输入要执行的函数名和参数，最后在设备上运行。
    -   **`在Android设备上运行JS`**: 用于直接运行一个已经编译好的 `.js` 文件。流程与上面类似，但不会执行编译步骤。
4.  点击绿色的“启动调试”按钮 (F5)。
5.  根据顶部弹出的提示框，依次输入**函数名**和 **JSON 格式的参数**，然后按回车。

VS Code 会自动打开一个新的终端面板，并执行相应的脚本，你可以在该面板中看到 `adb logcat` 的实时输出。这个集成化的工作流极大地提升了开发和调试的效率。 

## 10. VS Code 配置 (可选)

为了安全和避免不同开发者环境的冲突，`.vscode` 文件夹通常会被添加到 `.gitignore` 中，本项目也不例外。这意味着 `launch.json` 和 `tasks.json` 这两个配置文件不会被提交到版本控制中。

如果你想使用上一章节描述的 VS Code 一键启动功能，你需要手动在项目根目录创建这些文件。

### 10.1. 创建 tasks.json

1.  在项目根目录创建一个名为 `.vscode` 的文件夹。
2.  在 `.vscode` 文件夹内，创建一个名为 `tasks.json` 的文件。
3.  将以下内容复制并粘贴到 `tasks.json` 文件中：

```json
{
    "version": "2.0.0",
    "tasks": [
        {
            "label": "运行JavaScript到Android设备",
            "type": "shell",
            "command": ".\\tools\\execute_js.bat",
            "args": [
                "${fileDirname}\\${fileBasenameNoExtension}.js",
                "${input:jsFunction}",
                "${input:jsParameters}"
            ],
            "windows": {
                "command": ".\\tools\\execute_js.bat"
            },
            "linux": {
                "command": "./tools/execute_js.sh"
            },
            "osx": {
                "command": "./tools/execute_js.sh"
            },
            "problemMatcher": [],
            "group": {
                "kind": "build",
                "isDefault": true
            },
            "presentation": {
                "reveal": "always",
                "panel": "new",
                "focus": true
            }
        },
        {
            "label": "tsc-watch",
            "type": "shell",
            "command": "tsc",
            "args": [
                "--watch",
                "--project",
                "."
            ],
            "isBackground": true,
            "problemMatcher": "$tsc-watch",
            "group": "build",
            "presentation": {
                "reveal": "always",
                "panel": "dedicated",
                "focus": false
            }
        },
        {
            "label": "运行TypeScript到Android设备",
            "dependsOn": [
                "tsc-watch"
            ],
            "dependsOrder": "sequence",
            "type": "shell",
            "command": ".\\tools\\execute_js.bat",
            "args": [
                "${fileDirname}\\${fileBasenameNoExtension}.js",
                "${input:jsFunction}",
                "${input:jsParameters}"
            ],
            "windows": {
                "command": ".\\tools\\execute_js.bat"
            },
            "linux": {
                "command": "./tools/execute_js.sh"
            },
            "osx": {
                "command": "./tools/execute_js.sh"
            },
            "problemMatcher": [],
            "group": "test",
            "presentation": {
                "reveal": "always",
                "panel": "new",
                "focus": true
            }
        }
    ],
    "inputs": [
        {
            "id": "jsFunction",
            "description": "要执行的JavaScript函数名",
            "default": "main",
            "type": "promptString"
        },
        {
            "id": "jsParameters",
            "description": "函数参数(JSON格式)",
            "default": "{}",
            "type": "promptString"
        }
    ]
}
```

### 10.2. 创建 launch.json

1.  同样在 `.vscode` 文件夹内，创建一个名为 `launch.json` 的文件。
2.  将以下内容复制并粘贴到 `launch.json` 文件中：

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "name": "Python Debugger: Current File",
            "type": "debugpy",
            "request": "launch",
            "program": "${file}",
            "console": "integratedTerminal"
        },
        {
            "name": "在Android设备上运行JS",
            "type": "node",
            "request": "launch",
            "preLaunchTask": "运行JavaScript到Android设备",
            "presentation": {
                "hidden": false,
                "group": "",
                "order": 1
            }
        },
        {
            "name": "在Android设备上运行TS (编译+运行)",
            "type": "node",
            "request": "launch",
            "preLaunchTask": "运行TypeScript到Android设备",
            "presentation": {
                "hidden": false,
                "group": "",
                "order": 2
            }
        }
    ]
}
```

完成这两个文件的创建后，重启 VS Code，“运行和调试”中的配置项就应该可用了。 