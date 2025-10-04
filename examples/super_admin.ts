/*
METADATA
{
    "name": "super_admin",
    "description": "超级管理员工具集，提供终端命令和Shell操作的高级功能。terminal工具运行在Ubuntu环境中（已正确挂载sdcard和storage），shell工具通过Shizuku/Root直接执行Android系统命令。适合需要进行底层系统管理和命令行操作的场景。",
    "enabledByDefault": true,
    "tools": [
        {
            "name": "terminal",
            "description": "在Ubuntu环境中执行命令并收集输出结果。运行环境：完整的Ubuntu系统，已正确挂载sdcard和storage目录，可访问Android存储空间。会自动保留目录上下文。注意：不支持交互式命令，执行需要交互的命令（如apt install）时，请使用-y等参数以避免阻塞。",
            "parameters": [
                {
                    "name": "command",
                    "description": "要执行的命令",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "sessionId",
                    "description": "可选的会话ID，用于使用特定的终端会话",
                    "type": "string",
                    "required": false
                },
                {
                    "name": "timeoutMs",
                    "description": "可选的超时时间（毫秒）",
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "shell",
            "description": "通过Shizuku/Root权限直接在Android系统中执行Shell命令。运行环境：直接访问Android系统，具有系统级权限，适用于需要操作Android系统底层的场景（如pm、am等系统命令）。",
            "parameters": [
                {
                    "name": "command",
                    "description": "要执行的Shell命令",
                    "type": "string",
                    "required": true
                }
            ]
        }
    ],
    "category": "SYSTEM_OPERATION"
}
*/

const superAdmin = (function () {
    /**
     * 在Ubuntu环境中执行终端命令并收集输出结果
     * 运行环境：完整的Ubuntu系统，已正确挂载sdcard和storage目录
     * @param command - 要执行的命令
     * @param sessionId - 可选的会话ID，用于使用特定的终端会话
     * @param timeoutMs - 可选的超时时间（毫秒）
     */
    async function terminal(params: { command: string, sessionId?: string, timeoutMs?: string }): Promise<any> {
        try {
            if (!params.command) {
                throw new Error("命令不能为空");
            }

            const command = params.command;
            let sessionId = params.sessionId;
            const timeoutMs = params.timeoutMs;

            console.log(`执行终端命令: ${command}`);

            // 将超时时间转换为数字类型
            const timeout = timeoutMs ? parseInt(timeoutMs, 10) : undefined;

            // 如果没有提供会话ID，则创建或获取一个默认会话
            if (!sessionId) {
                const session = await Tools.System.terminal.create("super_admin_default_session");
                sessionId = session.sessionId;
            }

            // 调用系统工具执行终端命令
            const result = await Tools.System.terminal.exec(sessionId!, command, timeout);

            return {
                command: command,
                output: result.output,
                exitCode: result.exitCode,
                sessionId: result.sessionId,
                context_preserved: true // 标记此命令保留了目录上下文
            };
        } catch (error) {
            console.error(`[terminal] 错误: ${error.message}`);
            console.error(error.stack);

            throw error;
        }
    }

    /**
     * 通过Shizuku/Root权限在Android系统中执行Shell命令
     * 运行环境：直接访问Android系统，具有系统级权限
     * @param command - 要执行的Shell命令
     */
    async function shell(params: { command: string }): Promise<any> {
        try {
            if (!params.command) {
                throw new Error("命令不能为空");
            }
            const command = params.command;

            console.log(`执行Shell命令: ${command}`);

            // 通过Shizuku/Root权限执行shell操作
            const result = await Tools.System.shell(`${command}`);

            return {
                command: command,
                output: result.output,
                exitCode: result.exitCode
            };
        } catch (error) {
            console.error(`[shell] 错误: ${error.message}`);
            console.error(error.stack);

            throw error;
        }
    }

    return {
        terminal,
        shell
    };
})();

// 逐个导出
exports.terminal = superAdmin.terminal;
exports.shell = superAdmin.shell; 