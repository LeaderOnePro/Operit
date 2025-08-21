/*
METADATA
{
    "name": "daily_life",
    "description": "日常生活工具集合，提供丰富的日常功能接口，包括日期时间查询、设备状态监测、天气搜索、提醒闹钟设置、短信电话通讯等。通过系统Intent实现各类日常任务，支持用户便捷地完成日常交互需求。",
    "tools": [
        {
            "name": "get_current_date",
            "description": "获取当前日期和时间，支持多种格式展示",
            "parameters": [
                {
                    "name": "format",
                    "description": "日期格式（'short'简短格式, 'medium'中等格式, 'long'完整格式，或自定义格式）",
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "device_status",
            "description": "获取设备状态信息，包括电池和内存使用情况",
            "parameters": []
        },
        {
            "name": "set_reminder",
            "description": "创建提醒或待办事项。",
            "parameters": [
                {
                    "name": "title",
                    "description": "提醒或待办事项的标题",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "description",
                    "description": "提醒的附加详细信息",
                    "type": "string",
                    "required": false
                },
                {
                    "name": "due_date",
                    "description": "提醒的到期日期（ISO字符串格式）",
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "set_alarm",
            "description": "在设备上设置闹钟。",
            "parameters": [
                {
                    "name": "hour",
                    "description": "闹钟小时（0-23）",
                    "type": "number",
                    "required": true
                },
                {
                    "name": "minute",
                    "description": "闹钟分钟（0-59）",
                    "type": "number",
                    "required": true
                },
                {
                    "name": "message",
                    "description": "闹钟标签",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "days",
                    "description": "重复闹钟的天数（数字数组，1=周日，7=周六）",
                    "type": "array",
                    "required": false
                }
            ]
        },
        {
            "name": "send_message",
            "description": "发送短信",
            "parameters": [
                {
                    "name": "phone_number",
                    "description": "接收者电话号码",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "message",
                    "description": "短信内容",
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "make_phone_call",
            "description": "拨打电话",
            "parameters": [
                {
                    "name": "phone_number",
                    "description": "要拨打的电话号码",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "emergency",
                    "description": "是否为紧急呼叫",
                    "type": "boolean",
                    "required": false
                }
            ]
        },
        {
            "name": "search_weather",
            "description": "搜索当前天气信息",
            "parameters": [
                {
                    "name": "location",
                    "description": "要查询天气的位置（城市名称或'current'表示当前位置）",
                    "type": "string",
                    "required": false
                }
            ]
        }
    ],
    "category": "SYSTEM_OPERATION"
}
*/

const dailyLife = (function () {
    /**
     * Get the current date and time in various formats
     * @param params - Optional parameters including format
     */
    async function get_current_date(params: { format?: string }): Promise<any> {
        try {
            const format = params.format || 'medium';
            const now = new Date();

            let formattedDate: string;

            switch (format) {
                case 'short':
                    formattedDate = now.toLocaleDateString();
                    break;
                case 'long':
                    formattedDate = now.toLocaleDateString(undefined, {
                        weekday: 'long',
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                        second: '2-digit'
                    });
                    break;
                case 'medium':
                default:
                    formattedDate = now.toLocaleDateString(undefined, {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit'
                    });
                    break;
            }

            return {
                timestamp: now.getTime(),
                iso: now.toISOString(),
                formatted: formattedDate,
                date: {
                    year: now.getFullYear(),
                    month: now.getMonth() + 1,
                    day: now.getDate(),
                    weekday: now.toLocaleDateString(undefined, { weekday: 'long' })
                },
                time: {
                    hours: now.getHours(),
                    minutes: now.getMinutes(),
                    seconds: now.getSeconds()
                }
            };
        } catch (error) {
            console.error(`[get_current_date] 错误: ${error.message}`);
            console.error(error.stack);
            throw error;
        }
    }

    /**
     * Get device status information
     */
    async function device_status(): Promise<any> {
        try {
            // Get device information using the System tool
            const deviceInfo = await Tools.System.getDeviceInfo();

            return {
                battery: {
                    level: deviceInfo.batteryLevel,
                    charging: deviceInfo.batteryCharging
                },
                memory: {
                    total: deviceInfo.totalMemory,
                    available: deviceInfo.availableMemory
                },
                storage: {
                    total: deviceInfo.totalStorage,
                    available: deviceInfo.availableStorage
                },
                device: {
                    model: deviceInfo.model,
                    manufacturer: deviceInfo.manufacturer,
                    androidVersion: deviceInfo.androidVersion
                },
                network: deviceInfo.networkType
            };
        } catch (error) {
            throw new Error(`Failed to get device status: ${error.message}`);
        }
    }

    /**
     * Search for current weather information
     * @param params - Parameters with optional location
     */
    async function search_weather(params: { location?: string }): Promise<any> {
        try {
            // Default to "当前天气" if no location is provided
            const location = params.location || "current";

            // 构造搜索URL
            const searchUrl = location === "current" ?
                "https://www.baidu.com/s?wd=当前天气" :
                `https://www.baidu.com/s?wd=${encodeURIComponent(location + " 天气")}`;

            console.log(`搜索天气信息: ${searchUrl}`);

            // 使用visit_web工具直接访问搜索页面
            const result = await Tools.Net.visit(searchUrl);

            // 从网页内容中提取有用的信息
            // 由于结果现在是页面内容而非结构化搜索结果，我们需要提取有用信息
            const extractedInfo = extractWeatherInfo(result.content);

            return {
                success: true,
                query: location === "current" ? "当前天气" : `${location} 天气`,
                location: location,
                timestamp: new Date().toISOString(),
                url: result.url,
                title: result.title,
                weather_info: extractedInfo,
                note: "天气数据来自网页内容提取，仅供参考。"
            };
        } catch (error) {
            console.error(`[search_weather] 错误: ${error.message}`);
            console.error(error.stack);

            throw new Error(`获取天气信息失败: ${error.message}`);
        }
    }

    /**
     * 从网页内容中提取天气信息
     * @param content - 网页内容
     * @returns 提取的天气信息
     */
    function extractWeatherInfo(content: string): string {
        // 直接返回页面内容的一部分，保留更多原始信息
        // 移除一些可能的HTML标签，但不做过度处理
        const cleanContent = content
            .replace(/<\/?[^>]+(>|$)/g, " ") // 简单移除HTML标签
            .replace(/\s+/g, " ")            // 压缩多余空白
            .trim();

        // 返回更大篇幅的内容
        const maxLength = 1500; // 返回更多内容

        return cleanContent.length > maxLength ?
            cleanContent.substring(0, maxLength) + "..." :
            cleanContent;
    }

    /**
     * Set a reminder or to-do item
     * @param params - Parameters with reminder details
     */
    async function set_reminder(params: { title: string; description?: string; due_date?: string }): Promise<any> {
        try {
            if (!params.title) {
                throw new Error("Reminder title is required");
            }

            console.log("创建提醒...");
            console.log("尝试使用隐式Intent创建日历事件...");

            // 创建Intent
            const intent = new Intent(IntentAction.ACTION_INSERT);
            // 设置日历事件的URI
            intent.setData("content://com.android.calendar/events");

            // 设置事件详情
            intent.putExtra("title", params.title);
            if (params.description) {
                intent.putExtra("description", params.description);
            }

            // 处理日期
            if (params.due_date) {
                const dueDate = new Date(params.due_date);
                // 使用毫秒级时间戳
                const beginTime = dueDate.getTime();
                const endTime = beginTime + 3600000; // 默认1小时后
                intent.putExtra("beginTime", beginTime);
                intent.putExtra("endTime", endTime);

                // 添加单独的日期和时间组件，增加兼容性
                intent.putExtra("eventTimezone", "UTC");
                intent.putExtra("allDay", false);
            } else {
                // 默认使用当前时间后1小时
                const now = new Date();
                const beginTime = now.getTime();
                const endTime = beginTime + 3600000;
                intent.putExtra("beginTime", beginTime);
                intent.putExtra("endTime", endTime);

                // 添加单独的日期和时间组件
                intent.putExtra("eventTimezone", "UTC");
                intent.putExtra("allDay", false);
            }

            // 设置提醒
            intent.putExtra("hasAlarm", 1);

            // 添加标志
            intent.addFlag(IntentFlag.ACTIVITY_NEW_TASK);

            // 启动Intent
            const result = await intent.start();

            // 返回结果
            return {
                success: true,
                message: "提醒创建成功",
                title: params.title,
                description: params.description || undefined,
                due_date: params.due_date || undefined,
                method: "implicit_intent",
                raw_result: result
            };
        } catch (error) {
            console.error(`[set_reminder] 错误: ${error.message}`);
            console.error(error.stack);

            return {
                success: false,
                message: `创建提醒失败: ${error.message}`,
                title: params.title,
                description: params.description || undefined,
                due_date: params.due_date || undefined,
                error: error.message
            };
        }
    }

    /**
     * Set an alarm on the device
     * @param params - Parameters with alarm details
     */
    async function set_alarm(params: { hour: number | string; minute: number | string; message: string; days?: (number | string)[] | string }): Promise<any> {
        try {
            if (params.hour === undefined || params.minute === undefined) {
                throw new Error("Hour and minute are required for setting an alarm");
            }
            if (typeof params.hour === 'string') {
                params.hour = Number(params.hour);
            }
            if (typeof params.minute === 'string') {
                params.minute = Number(params.minute);
            }
            if (params.days) {
                if (typeof params.days === 'string') {
                    params.days = JSON.parse(params.days);
                }
                if (Array.isArray(params.days)) {
                    params.days = params.days.map(day => {
                        if (typeof day === 'string') {
                            return Number(day);
                        }
                        return day;
                    });
                } else {
                    console.error("Invalid days format");
                    params.days = [];
                }
            }

            if (params.hour < 0 || params.hour > 23) {
                throw new Error("Hour must be between 0 and 23");
            }

            if (params.minute < 0 || params.minute > 59) {
                throw new Error("Minute must be between 0 and 59");
            }

            console.log("设置闹钟..." + params.hour + ":" + params.minute);
            console.log("尝试使用隐式Intent设置闹钟...");

            // 创建Intent - 使用Android的标准闹钟Intent Action
            const intent = new Intent("android.intent.action.SET_ALARM");
            // 或者也可以使用常量: new Intent(IntentAction.ACTION_MAIN);

            // 设置闹钟详情 - 使用正确的参数名称
            intent.putExtra("android.intent.extra.alarm.HOUR", params.hour);
            intent.putExtra("android.intent.extra.alarm.MINUTES", params.minute);

            // 添加标签
            intent.putExtra("android.intent.extra.alarm.MESSAGE", params.message);


            // 设置重复日期（如果提供）
            if (params.days && params.days.length > 0) {
                intent.putExtra("android.intent.extra.alarm.DAYS", params.days);
            }

            // 不跳过UI确认 - 这可能是问题所在，某些设备需要用户确认
            // intent.putExtra("android.intent.extra.alarm.SKIP_UI", true);

            // 设置闹钟应该立即响铃
            intent.putExtra("android.intent.extra.alarm.VIBRATE", true);

            // 添加标志 - 必须使用NEW_TASK标志来启动Activity
            intent.addFlag(IntentFlag.ACTIVITY_NEW_TASK);

            // 确保Intent被视为Activity启动而不是广播
            // 手动添加DEFAULT类别以确保Intent可以被正确处理
            intent.addCategory("android.intent.category.DEFAULT");

            // 启动Intent - 这会显示闹钟设置界面
            const result = await intent.start();

            // 返回结果
            return {
                success: true,
                message: "闹钟设置成功",
                alarm_time: `${params.hour.toString().padStart(2, '0')}:${params.minute.toString().padStart(2, '0')}`,
                label: params.message || undefined,
                repeat_days: params.days || undefined,
                method: "implicit_intent",
                raw_result: result
            };
        } catch (error) {
            console.error(`[set_alarm] 错误: ${error.message}`);
            console.error(error.stack);

            return {
                success: false,
                message: `设置闹钟失败: ${error.message}`,
                alarm_time: `${params.hour.toString().padStart(2, '0')}:${params.minute.toString().padStart(2, '0')}`,
                label: params.message || undefined,
                repeat_days: params.days || undefined,
                error: error.message
            };
        }
    }

    /**
     * Send a text message
     * @param params - Parameters with message details
     */
    async function send_message(params: { phone_number: string; message: string }): Promise<any> {
        try {
            if (!params.phone_number) {
                throw new Error("Phone number is required");
            }

            if (!params.message) {
                throw new Error("Message content is required");
            }

            console.log(`发送短信: ${params.phone_number}`);

            // 创建短信Intent
            const intent = new Intent(IntentAction.ACTION_SENDTO);

            // 设置短信URI
            const smsUri = `smsto:${params.phone_number}`;
            intent.setData(smsUri);

            // 添加短信内容
            intent.putExtra("sms_body", params.message);

            // 添加必要的标志
            intent.addFlag(IntentFlag.ACTIVITY_NEW_TASK);

            // 启动Intent
            const result = await intent.start();

            return {
                success: true,
                message: "短信编辑界面已打开",
                phone_number: params.phone_number,
                content_preview: params.message.length > 30 ? params.message.substring(0, 30) + "..." : params.message,
                raw_result: result
            };
        } catch (error) {
            console.error(`发送短信失败: ${error.message}`);
            return {
                success: false,
                message: `发送短信失败: ${error.message}`,
                phone_number: params.phone_number
            };
        }
    }

    /**
     * Make a phone call
     * @param params - Parameters with call details
     */
    async function make_phone_call(params: { phone_number: string; emergency?: boolean | string }): Promise<any> {
        try {
            if (!params.phone_number) {
                throw new Error("Phone number is required");
            }

            console.log(`拨打电话: ${params.phone_number}`);

            // 选择合适的Intent Action
            // 如果是紧急电话，使用ACTION_CALL_EMERGENCY，否则使用ACTION_DIAL
            if (typeof params.emergency === 'string') {
                params.emergency = params.emergency === 'true';
            }

            const action = params.emergency ? IntentAction.ACTION_CALL_EMERGENCY : IntentAction.ACTION_DIAL;

            // 创建拨号Intent
            const intent = new Intent(action);

            // 设置电话URI
            const phoneUri = `tel:${params.phone_number}`;
            intent.setData(phoneUri);

            // 添加必要的标志
            intent.addFlag(IntentFlag.ACTIVITY_NEW_TASK);

            // 启动Intent
            const result = await intent.start();

            return {
                success: true,
                message: params.emergency ? "紧急电话已拨打" : "拨号界面已打开",
                phone_number: params.phone_number,
                is_emergency: params.emergency || false,
                raw_result: result
            };
        } catch (error) {
            console.error(`拨打电话失败: ${error.message}`);
            return {
                success: false,
                message: `拨打电话失败: ${error.message}`,
                phone_number: params.phone_number,
                is_emergency: params.emergency || false
            };
        }
    }

    /**
     * 等待指定的毫秒数
     * @param ms 等待的毫秒数
     */
    async function sleep(ms: number | string): Promise<void> {
        const sleepTime = Number(ms);
        if (isNaN(sleepTime)) {
            throw new Error("Invalid sleep time");
        }
        return new Promise(resolve => setTimeout(resolve, sleepTime));
    }

    /**
     * Test and demonstrate all daily life functions
     * This function shows examples of all available daily life functions
     */
    async function main(): Promise<any> {
        try {
            // 创建测试结果对象 - 添加接口定义以解决类型错误
            const results: {
                date?: any;
                device?: any;
                weather?: any;
                reminder?: any;
                alarm?: any;
                message?: any;
                call?: any;
            } = {};

            // 1. 测试当前日期时间函数
            console.log("测试获取当前日期时间...");
            try {
                const dateResult = await get_current_date({});
                results.date = dateResult;
                console.log("✓ 日期时间获取成功");
            } catch (error) {
                results.date = { error: `获取日期时间失败: ${error.message}` };
                console.log("✗ 日期时间获取失败");
            }

            // 2. 测试设备状态函数
            console.log("测试获取设备状态...");
            try {
                const deviceResult = await device_status();
                results.device = deviceResult;
                console.log("✓ 设备状态获取成功");
            } catch (error) {
                results.device = { error: `获取设备状态失败: ${error.message}` };
                console.log("✗ 设备状态获取失败");
            }

            // 3. 测试天气搜索功能
            console.log("测试天气搜索...");
            try {
                const weatherResult = await search_weather({ location: "current" });
                results.weather = weatherResult;
                console.log("✓ 天气搜索成功");
            } catch (error) {
                results.weather = { error: `天气搜索失败: ${error.message}` };
                console.log("✗ 天气搜索失败");
            }

            // 4. 测试设置提醒功能
            // console.log("测试设置提醒...");
            // try {
            //     const reminderResult = await set_reminder({
            //         title: "测试提醒",
            //         description: "这是一个测试提醒",
            //         // 可以设置未来时间
            //         due_date: new Date(Date.now() + 3600000).toISOString() // 1小时后
            //     });
            //     results.reminder = reminderResult;
            //     console.log("✓ 设置提醒成功");
            // } catch (error) {
            //     results.reminder = { error: `设置提醒失败: ${error.message}` };
            //     console.log("✗ 设置提醒失败");
            // }

            // 5. 测试设置闹钟功能
            console.log("测试设置闹钟...");
            try {
                // 获取当前时间，设置为5分钟后的闹钟
                const now = new Date();
                const hour = now.getHours();
                const minute = (now.getMinutes() + 5) % 60;

                const alarmResult = await set_alarm({
                    hour: hour.toString(),
                    minute: minute.toString(),
                    message: "测试闹钟",
                    // 可以添加重复日期测试，例如每周一和周五
                    // days: [2, 6]  // 2表示周一，6表示周五
                });
                results.alarm = alarmResult;
                console.log("✓ 设置闹钟成功");
            } catch (error) {
                results.alarm = { error: `设置闹钟失败: ${error.message}` };
                console.log("✗ 设置闹钟失败");
            }

            // 6. 测试发送消息功能
            console.log("测试发送短信功能...");
            try {
                // 使用测试号码 - 常用的中国运营商服务号码，适合测试
                const testPhoneNumber = "10086";
                const testMessage = "这是一条测试短信，不会实际发送";

                // 直接调用发送短信功能
                const smsResult = await send_message({
                    phone_number: testPhoneNumber,
                    message: testMessage
                });
                results.message = smsResult;
                console.log("✓ 短信测试界面打开成功");

                // 等待用户查看短信界面
                await sleep(5000);
            } catch (error) {
                results.message = { error: `短信测试失败: ${error.message}` };
                console.log("✗ 短信测试失败");
            }

            // 7. 测试拨打电话功能
            console.log("测试拨号功能...");
            try {
                // 使用测试号码 - 常用的中国运营商服务号码，适合测试
                const testPhoneNumber = "10086";

                // 直接调用拨号功能
                const dialResult = await make_phone_call({
                    phone_number: testPhoneNumber,
                    emergency: false
                });
                results.call = dialResult;
                console.log("✓ 拨号界面打开成功");

                // 等待用户查看拨号界面
                await sleep(5000);
            } catch (error) {
                results.call = { error: `拨号测试失败: ${error.message}` };
                console.log("✗ 拨号测试失败");
            }

            // 返回所有测试结果
            return {
                message: "日常生活功能测试完成",
                test_results: results,
                timestamp: new Date().toISOString(),
                summary: "测试了各种日常生活功能，包括天气搜索、拨号和短信测试。请查看各功能的测试结果。"
            };
        } catch (error) {
            return {
                success: false,
                message: `测试过程中发生错误: ${error.message}`
            };
        }
    }

    /**
     * 包装函数 - 统一处理所有daily_life函数的返回结果
     * @param func 原始函数
     * @param params 函数参数
     * @param successMessage 成功消息
     * @param failMessage 失败消息
     * @param additionalInfo 附加信息(可选)
     */
    async function daily_wrap<T>(
        func: (params: any) => Promise<any>,
        params: any,
        successMessage: string,
        failMessage: string,
        additionalInfo: string = ""
    ): Promise<void> {
        try {
            console.log(`开始执行函数: ${func.name || '匿名函数'}`);
            console.log(`参数:`, JSON.stringify(params, undefined, 2));

            // 执行原始函数
            const result = await func(params);

            console.log(`函数 ${func.name || '匿名函数'} 执行结果:`, JSON.stringify(result, undefined, 2));

            // 如果原始函数已经调用了complete，就不需要再次调用
            if (result === undefined) return;

            // 根据结果类型处理
            if (typeof result === "boolean") {
                // 布尔类型结果
                complete({
                    success: result,
                    message: result ? successMessage : failMessage,
                    additionalInfo: additionalInfo
                });
            } else {
                // 数据类型结果
                complete({
                    success: true,
                    message: successMessage,
                    additionalInfo: additionalInfo,
                    data: result
                });
            }
        } catch (error) {
            // 详细记录错误信息
            console.error(`函数 ${func.name || '匿名函数'} 执行失败!`);
            console.error(`错误信息: ${error.message}`);
            console.error(`错误堆栈: ${error.stack}`);

            // 处理错误
            complete({
                success: false,
                message: `${failMessage}: ${error.message}`,
                additionalInfo: additionalInfo,
                error_stack: error.stack
            });
        }
    }

    return {
        get_current_date: async (params) => await daily_wrap(
            get_current_date,
            params,
            "获取日期时间成功",
            "获取日期时间失败"
        ),
        device_status: async (params) => await daily_wrap(
            device_status,
            params,
            "获取设备状态成功",
            "获取设备状态失败"
        ),
        search_weather: async (params) => await daily_wrap(
            search_weather,
            params,
            "获取天气信息成功",
            "获取天气信息失败"
        ),
        set_reminder: async (params) => await daily_wrap(
            set_reminder,
            params,
            "设置提醒成功",
            "设置提醒失败"
        ),
        set_alarm: async (params) => await daily_wrap(
            set_alarm,
            params,
            "设置闹钟成功",
            "设置闹钟失败"
        ),
        send_message: async (params) => await daily_wrap(
            send_message,
            params,
            "发送消息成功",
            "发送消息失败"
        ),
        make_phone_call: async (params) => await daily_wrap(
            make_phone_call,
            params,
            "拨打电话成功",
            "拨打电话失败"
        ),
        main: async (params) => await daily_wrap(
            main,
            params,
            "测试完成",
            "测试失败"
        )
    }
})();

//逐个导出
exports.get_current_date = dailyLife.get_current_date;
exports.device_status = dailyLife.device_status;
exports.search_weather = dailyLife.search_weather;
exports.set_reminder = dailyLife.set_reminder;
exports.set_alarm = dailyLife.set_alarm;
exports.send_message = dailyLife.send_message;
exports.make_phone_call = dailyLife.make_phone_call;
exports.main = dailyLife.main;