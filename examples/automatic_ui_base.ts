/* METADATA
{
    "name": "Automatic_ui_base",
    "description": "提供基本的UI自动化工具，用于模拟用户在设备屏幕上的交互。",
    "tools": [
        {
            "name": "usage_advice",
            "description": "UI自动化建议：\\n- 元素定位选项：\\n  • 列表：使用index参数（例如，“点击索引为2的列表项”）\\n  • 文本：使用bounds或partialMatch进行模糊匹配（例如，“点击包含‘登录’文字的按钮”）\\n- 操作链：组合多个操作以完成复杂任务（例如，“获取页面信息，然后点击元素”）\\n- 错误处理：如果操作失败，分析页面信息找出原因，并尝试其他方法。",
            "parameters": []
        },
        {
            "name": "get_page_info",
            "description": "获取当前UI屏幕的信息，包括完整的UI层次结构。",
            "parameters": [
                { "name": "format", "description": "格式，可选：'xml'或'json'，默认'xml'", "type": "string", "required": false },
                { "name": "detail", "description": "详细程度，可选：'minimal'、'summary'或'full'，默认'summary'", "type": "string", "required": false }
            ]
        },
        {
            "name": "tap",
            "description": "在特定坐标模拟点击。",
            "parameters": [
                { "name": "x", "description": "X坐标", "type": "number", "required": true },
                { "name": "y", "description": "Y坐标", "type": "number", "required": true }
            ]
        },
        {
            "name": "click_element",
            "description": "点击由资源ID或类名标识的元素。必须至少提供一个标识参数。",
            "parameters": [
                { "name": "resourceId", "description": "元素资源ID", "type": "string", "required": false },
                { "name": "className", "description": "元素类名", "type": "string", "required": false },
                { "name": "index", "description": "要点击的匹配元素，从0开始计数，默认0", "type": "number", "required": false },
                { "name": "partialMatch", "description": "是否启用部分匹配，默认false", "type": "boolean", "required": false },
                { "name": "bounds", "description": "元素边界，格式为'[left,top][right,bottom]'", "type": "string", "required": false }
            ]
        },
        {
            "name": "set_input_text",
            "description": "在输入字段中设置文本。",
            "parameters": [
                { "name": "text", "description": "要输入的文本", "type": "string", "required": true }
            ]
        },
        {
            "name": "press_key",
            "description": "模拟按键。",
            "parameters": [
                { "name": "key_code", "description": "键码，例如'KEYCODE_BACK'、'KEYCODE_HOME'等", "type": "string", "required": true }
            ]
        },
        {
            "name": "swipe",
            "description": "模拟滑动手势。",
            "parameters": [
                { "name": "start_x", "description": "起始X坐标", "type": "number", "required": true },
                { "name": "start_y", "description": "起始Y坐标", "type": "number", "required": true },
                { "name": "end_x", "description": "结束X坐标", "type": "number", "required": true },
                { "name": "end_y", "description": "结束Y坐标", "type": "number", "required": true },
                { "name": "duration", "description": "持续时间，毫秒，默认300", "type": "number", "required": false }
            ]
        }
    ],
    "category": "UI_AUTOMATION"
}
*/

const UIAutomationTools = (function () {

    interface ToolResponse {
        success: boolean;
        message: string;
        data?: any;
    }

    async function get_page_info(params: { format?: 'xml' | 'json', detail?: 'minimal' | 'summary' | 'full' }): Promise<ToolResponse> {
        const result = await Tools.UI.getPageInfo();
        return { success: true, message: '成功获取页面信息', data: result };
    }

    async function tap(params: { x: number, y: number }): Promise<ToolResponse> {
        const result = await Tools.UI.tap(params.x, params.y);
        return { success: true, message: '点击操作成功', data: result };
    }

    async function click_element(params: { resourceId?: string, className?: string, index?: number, partialMatch?: boolean, bounds?: string }): Promise<ToolResponse> {
        const result = await Tools.UI.clickElement(params);
        return { success: true, message: '点击元素操作成功', data: result };
    }

    async function set_input_text(params: { text: string }): Promise<ToolResponse> {
        const result = await Tools.UI.setText(params.text);
        return { success: true, message: '输入文本操作成功', data: result };
    }

    async function press_key(params: { key_code: string }): Promise<ToolResponse> {
        const result = await Tools.UI.pressKey(params.key_code);
        return { success: true, message: '按键操作成功', data: result };
    }

    async function swipe(params: { start_x: number, start_y: number, end_x: number, end_y: number, duration?: number }): Promise<ToolResponse> {
        const result = await Tools.UI.swipe(params.start_x, params.start_y, params.end_x, params.end_y);
        return { success: true, message: '滑动操作成功', data: result };
    }

    async function wrapToolExecution<P>(func: (params: P) => Promise<ToolResponse>, params: P) {
        try {
            const result = await func(params);
            complete(result);
        } catch (error: any) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }

    async function main() {
        console.log("UI Automation Tools package main function. This is for testing and demonstration.");
        complete({ success: true, message: "UI Automation Tools main finished." });
    }

    return {
        get_page_info: (params: { format?: 'xml' | 'json', detail?: 'minimal' | 'summary' | 'full' }) => wrapToolExecution(get_page_info, params),
        tap: (params: { x: number, y: number }) => wrapToolExecution(tap, params),
        click_element: (params: { resourceId?: string, className?: string, index?: number, partialMatch?: boolean, bounds?: string }) => wrapToolExecution(click_element, params),
        set_input_text: (params: { text: string }) => wrapToolExecution(set_input_text, params),
        press_key: (params: { key_code: string }) => wrapToolExecution(press_key, params),
        swipe: (params: { start_x: number, start_y: number, end_x: number, end_y: number, duration?: number }) => wrapToolExecution(swipe, params),
        main,
    };
})();

exports.get_page_info = UIAutomationTools.get_page_info;
exports.tap = UIAutomationTools.tap;
exports.click_element = UIAutomationTools.click_element;
exports.set_input_text = UIAutomationTools.set_input_text;
exports.press_key = UIAutomationTools.press_key;
exports.swipe = UIAutomationTools.swipe;
exports.main = UIAutomationTools.main; 