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
    async function get_page_info(params) {
        const result = (await UINode.getCurrentPage()).toFormattedString();
        return { success: true, message: '成功获取页面信息', data: result };
    }
    async function tap(params) {
        const result = await Tools.UI.tap(params.x, params.y);
        return { success: true, message: '点击操作成功', data: result };
    }
    async function click_element(params) {
        const result = await Tools.UI.clickElement(params);
        return { success: true, message: '点击元素操作成功', data: result };
    }
    async function set_input_text(params) {
        const result = await Tools.UI.setText(params.text);
        return { success: true, message: '输入文本操作成功', data: result };
    }
    async function press_key(params) {
        const result = await Tools.UI.pressKey(params.key_code);
        return { success: true, message: '按键操作成功', data: result };
    }
    async function swipe(params) {
        const result = await Tools.UI.swipe(params.start_x, params.start_y, params.end_x, params.end_y);
        return { success: true, message: '滑动操作成功', data: result };
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params);
            complete(result);
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }
    async function main() {
        console.log("=== UI Automation Tools 测试开始 ===\n");
        const results = [];
        try {
            // 1. 测试 get_page_info
            console.log("1. 测试 get_page_info...");
            const pageInfoResult = await get_page_info({});
            results.push({ tool: 'get_page_info', result: pageInfoResult });
            console.log("✓ get_page_info 测试完成\n");
            // 2. 测试 tap (点击屏幕中心位置)
            console.log("2. 测试 tap...");
            const tapResult = await tap({ x: 500, y: 1000 });
            results.push({ tool: 'tap', result: tapResult });
            console.log("✓ tap 测试完成\n");
            await Tools.System.sleep(500);
            // 3. 测试 press_key (按音量上键)
            console.log("3. 测试 press_key...");
            const pressKeyResult = await press_key({ key_code: 'KEYCODE_VOLUME_UP' });
            results.push({ tool: 'press_key', result: pressKeyResult });
            console.log("✓ press_key 测试完成\n");
            await Tools.System.sleep(500);
            // 4. 测试 set_input_text
            console.log("4. 测试 set_input_text...");
            const setTextResult = await set_input_text({ text: 'UI自动化测试文本' });
            results.push({ tool: 'set_input_text', result: setTextResult });
            console.log("✓ set_input_text 测试完成\n");
            await Tools.System.sleep(500);
            // 5. 测试 swipe (向上滑动)
            console.log("5. 测试 swipe...");
            const swipeResult = await swipe({
                start_x: 500,
                start_y: 1500,
                end_x: 500,
                end_y: 500,
                duration: 300
            });
            results.push({ tool: 'swipe', result: swipeResult });
            console.log("✓ swipe 测试完成\n");
            await Tools.System.sleep(500);
            // 6. 测试 click_element (尝试点击一个常见的元素)
            console.log("6. 测试 click_element...");
            try {
                const clickResult = await click_element({
                    className: 'android.widget.Button',
                    index: 0
                });
                results.push({ tool: 'click_element', result: clickResult });
                console.log("✓ click_element 测试完成\n");
            }
            catch (error) {
                console.log("⚠ click_element 测试失败（这可能是正常的，如果当前页面没有按钮）:", error.message, "\n");
                results.push({ tool: 'click_element', result: { success: false, message: error.message } });
            }
            console.log("=== UI Automation Tools 测试完成 ===\n");
            console.log("测试结果汇总:");
            results.forEach((r, i) => {
                const status = r.result.success ? '✓' : '✗';
                console.log(`${i + 1}. ${status} ${r.tool}: ${r.result.message}`);
            });
            complete({
                success: true,
                message: "所有UI工具测试完成",
                data: results
            });
        }
        catch (error) {
            console.error("测试过程中发生错误:", error);
            complete({
                success: false,
                message: `测试失败: ${error.message}`,
                data: results
            });
        }
    }
    return {
        get_page_info: (params) => wrapToolExecution(get_page_info, params),
        tap: (params) => wrapToolExecution(tap, params),
        click_element: (params) => wrapToolExecution(click_element, params),
        set_input_text: (params) => wrapToolExecution(set_input_text, params),
        press_key: (params) => wrapToolExecution(press_key, params),
        swipe: (params) => wrapToolExecution(swipe, params),
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
