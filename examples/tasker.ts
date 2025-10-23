/* METADATA
{
  "name": "tasker",
  "description": "集成 Tasker 插件事件触发工具，通过本包可向 Tasker 发送事件。",
  "enabledByDefault": true,
  "tools": [
    {
      "name": "trigger_tasker_event",
      "description": "触发一个 Tasker 事件。使用 task_type 指定事件类型，可传 arg1..arg5 或 args_json。",
      "parameters": [
        { "name": "task_type", "description": "事件类型标识", "type": "string", "required": true },
        { "name": "arg1", "description": "可选参数1", "type": "string", "required": false },
        { "name": "arg2", "description": "可选参数2", "type": "string", "required": false },
        { "name": "arg3", "description": "可选参数3", "type": "string", "required": false },
        { "name": "arg4", "description": "可选参数4", "type": "string", "required": false },
        { "name": "arg5", "description": "可选参数5", "type": "string", "required": false },
        { "name": "args_json", "description": "以JSON形式传递任意键值对（若提供则优先生效）", "type": "string", "required": false }
      ]
    }
  ],
  "category": "SYSTEM_OPERATION"
}
*/

/**
 * Tasker 事件触发参数
 */
interface TaskerEventParams {
    /** 事件类型标识 */
    task_type: string;
    /** 可选参数1 */
    arg1?: string | undefined;
    /** 可选参数2 */
    arg2?: string | undefined;
    /** 可选参数3 */
    arg3?: string | undefined;
    /** 可选参数4 */
    arg4?: string | undefined;
    /** 可选参数5 */
    arg5?: string | undefined;
    /** 以JSON形式传递任意键值对（若提供则优先生效） */
    args_json?: string | undefined;
    /** 索引签名以兼容 ToolParams */
    [key: string]: string | number | boolean | object | undefined;
}

/**
 * 工具执行结果
 */
interface ToolResponse {
    success: boolean;
    message: string;
    data?: any;
}

const TaskerIntegration = (function () {
    /**
     * 触发 Tasker 事件
     * @param params 事件参数
     * @returns 触发结果
     */
    async function trigger_tasker_event(params: TaskerEventParams): Promise<ToolResponse> {
        // 直接调用已注册的原生工具 trigger_tasker_event
        // toolCall 返回结构化结果的 data 部分
        const data = await toolCall("trigger_tasker_event", params as any);
        return {
            success: true,
            message: "Tasker 事件已触发",
            data
        };
    }

    /**
     * 包装工具执行，处理错误和结果
     * @param func 要执行的函数
     * @param params 函数参数
     */
    async function wrapToolExecution(func: (params: TaskerEventParams) => Promise<ToolResponse>, params: TaskerEventParams): Promise<void> {
        try {
            const result = await func(params || {} as TaskerEventParams);
            complete(result);
        } catch (error: any) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({ success: false, message: String(error && error.message ? error.message : error) });
        }
    }

    return {
        trigger_tasker_event: (params: TaskerEventParams) => wrapToolExecution(trigger_tasker_event, params)
    };
})();

exports.trigger_tasker_event = TaskerIntegration.trigger_tasker_event;

