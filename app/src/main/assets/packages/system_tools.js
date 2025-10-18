/* METADATA
{
    "name": "system_tools",
    "description": "提供系统级操作工具，包括设置管理、应用管理、通知获取、位置服务和设备信息查询。",
    "enabledByDefault": true,
    "tools": [
        {
            "name": "get_system_setting",
            "description": "获取系统设置的值。需要用户授权。",
            "parameters": [
                { "name": "setting", "description": "设置名称", "type": "string", "required": true },
                { "name": "namespace", "description": "命名空间：system/secure/global，默认system", "type": "string", "required": false }
            ]
        },
        {
            "name": "modify_system_setting",
            "description": "修改系统设置的值。需要用户授权。",
            "parameters": [
                { "name": "setting", "description": "设置名称", "type": "string", "required": true },
                { "name": "value", "description": "设置值", "type": "string", "required": true },
                { "name": "namespace", "description": "命名空间：system/secure/global，默认system", "type": "string", "required": false }
            ]
        },
        {
            "name": "install_app",
            "description": "安装应用程序。需要用户授权。",
            "parameters": [
                { "name": "apk_path", "description": "APK文件路径", "type": "string", "required": true }
            ]
        },
        {
            "name": "uninstall_app",
            "description": "卸载应用程序。需要用户授权。",
            "parameters": [
                { "name": "package_name", "description": "应用包名", "type": "string", "required": true },
                { "name": "keep_data", "description": "是否保留数据，默认false", "type": "boolean", "required": false }
            ]
        },
        {
            "name": "list_installed_apps",
            "description": "获取已安装应用程序列表。需要用户授权。",
            "parameters": [
                { "name": "include_system_apps", "description": "是否包含系统应用，默认false", "type": "boolean", "required": false }
            ]
        },
        {
            "name": "start_app",
            "description": "启动应用程序。需要用户授权。",
            "parameters": [
                { "name": "package_name", "description": "应用包名", "type": "string", "required": true },
                { "name": "activity", "description": "可选活动名称", "type": "string", "required": false }
            ]
        },
        {
            "name": "stop_app",
            "description": "停止正在运行的应用程序。需要用户授权。",
            "parameters": [
                { "name": "package_name", "description": "应用包名", "type": "string", "required": true }
            ]
        },
        {
            "name": "get_notifications",
            "description": "获取设备通知内容。",
            "parameters": [
                { "name": "limit", "description": "最大返回条数，默认10", "type": "number", "required": false },
                { "name": "include_ongoing", "description": "是否包含常驻通知，默认false", "type": "boolean", "required": false }
            ]
        },
        {
            "name": "get_device_location",
            "description": "获取设备当前位置信息。",
            "parameters": [
                { "name": "high_accuracy", "description": "是否使用高精度模式，默认false", "type": "boolean", "required": false },
                { "name": "timeout", "description": "超时时间（秒），默认10", "type": "number", "required": false }
            ]
        },
        {
            "name": "get_device_info",
            "description": "获取详细的设备信息，包括型号、操作系统版本、内存、存储、网络状态等。",
            "parameters": []
        }
    ],
    "category": "SYSTEM_OPERATION"
}
*/
const SystemTools = (function () {
    async function get_system_setting(params) {
        const result = await Tools.System.getSetting(params.setting, params.namespace || 'system');
        return { success: true, message: '成功获取系统设置', data: result };
    }
    async function modify_system_setting(params) {
        const result = await Tools.System.setSetting(params.setting, params.value, params.namespace || 'system');
        const success = result && result.value === params.value;
        return { success: success, message: success ? '成功修改系统设置' : '修改系统设置失败', data: result };
    }
    async function install_app(params) {
        const result = await Tools.System.installApp(params.apk_path);
        return { success: result.success, message: result.success ? '应用安装成功' : '应用安装失败', data: result };
    }
    async function uninstall_app(params) {
        const result = await Tools.System.uninstallApp(params.package_name);
        return { success: result.success, message: result.success ? '应用卸载成功' : '应用卸载失败', data: result };
    }
    async function list_installed_apps(params) {
        const result = await Tools.System.listApps(params.include_system_apps || false);
        return { success: true, message: '成功获取应用列表', data: result };
    }
    async function start_app(params) {
        const result = await Tools.System.startApp(params.package_name, params.activity);
        return { success: result.success, message: result.success ? '应用启动成功' : '应用启动失败', data: result };
    }
    async function stop_app(params) {
        const result = await Tools.System.stopApp(params.package_name);
        return { success: result.success, message: result.success ? '应用停止成功' : '应用停止失败', data: result };
    }
    async function get_notifications(params) {
        const result = await Tools.System.getNotifications(params.limit || 10, params.include_ongoing || false);
        return { success: true, message: '成功获取通知', data: result };
    }
    async function get_device_location(params) {
        const result = await Tools.System.getLocation(params.high_accuracy || false, params.timeout || 10);
        return { success: true, message: '成功获取位置信息', data: result };
    }
    async function get_device_info(params) {
        const result = await Tools.System.getDeviceInfo();
        return { success: true, message: '成功获取设备信息', data: result };
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
        console.log("=== System Tools 全面测试开始 ===\n");
        const results = [];
        try {
            // 1. 测试 get_device_info（新增工具）
            console.log("1. 测试 get_device_info...");
            try {
                const deviceInfoResult = await get_device_info({});
                results.push({ tool: 'get_device_info', result: deviceInfoResult });
                console.log("✓ get_device_info 测试完成");
                if (deviceInfoResult.data) {
                    console.log(`  设备信息: ${JSON.stringify(deviceInfoResult.data).substring(0, 100)}...`);
                }
                console.log();
            }
            catch (error) {
                console.log("⚠ get_device_info 测试失败:", error.message, "\n");
                results.push({ tool: 'get_device_info', result: { success: false, message: error.message } });
            }
            // 2. 测试 get_notifications
            console.log("2. 测试 get_notifications...");
            try {
                const notificationsResult = await get_notifications({ limit: 5, include_ongoing: false });
                results.push({ tool: 'get_notifications', result: notificationsResult });
                console.log("✓ get_notifications 测试完成");
                if (notificationsResult.data) {
                    console.log(`  获取到 ${notificationsResult.data.length || 0} 条通知`);
                }
                console.log();
            }
            catch (error) {
                console.log("⚠ get_notifications 测试失败:", error.message, "\n");
                results.push({ tool: 'get_notifications', result: { success: false, message: error.message } });
            }
            // 3. 测试 get_device_location
            console.log("3. 测试 get_device_location...");
            try {
                const locationResult = await get_device_location({ high_accuracy: false, timeout: 5 });
                results.push({ tool: 'get_device_location', result: locationResult });
                console.log("✓ get_device_location 测试完成");
                if (locationResult.data) {
                    console.log(`  位置: ${JSON.stringify(locationResult.data)}`);
                }
                console.log();
            }
            catch (error) {
                console.log("⚠ get_device_location 测试失败（可能需要位置权限）:", error.message, "\n");
                results.push({ tool: 'get_device_location', result: { success: false, message: error.message } });
            }
            // 4. 测试 list_installed_apps
            console.log("4. 测试 list_installed_apps...");
            try {
                const appsResult = await list_installed_apps({ include_system_apps: false });
                results.push({ tool: 'list_installed_apps', result: appsResult });
                console.log("✓ list_installed_apps 测试完成");
                if (appsResult.data) {
                    console.log(`  已安装应用数量: ${appsResult.data.length || 0}`);
                }
                console.log();
            }
            catch (error) {
                console.log("⚠ list_installed_apps 测试失败（可能需要用户授权）:", error.message, "\n");
                results.push({ tool: 'list_installed_apps', result: { success: false, message: error.message } });
            }
            // 5. 测试 get_system_setting（读取一个常见的系统设置）
            console.log("5. 测试 get_system_setting...");
            try {
                const settingResult = await get_system_setting({ setting: 'screen_brightness', namespace: 'system' });
                results.push({ tool: 'get_system_setting', result: settingResult });
                console.log("✓ get_system_setting 测试完成");
                if (settingResult.data) {
                    console.log(`  screen_brightness = ${settingResult.data.value || settingResult.data}`);
                }
                console.log();
            }
            catch (error) {
                console.log("⚠ get_system_setting 测试失败:", error.message, "\n");
                results.push({ tool: 'get_system_setting', result: { success: false, message: error.message } });
            }
            // 6-10. 其他需要用户授权或可能造成系统变更的工具，仅做说明不实际执行
            console.log("6-10. 跳过破坏性/需要特殊权限的工具测试:");
            console.log("  ⊘ modify_system_setting - 需要WRITE_SETTINGS权限，会修改系统设置");
            console.log("  ⊘ install_app - 需要INSTALL_PACKAGES权限");
            console.log("  ⊘ uninstall_app - 需要DELETE_PACKAGES权限");
            console.log("  ⊘ start_app - 需要应用包名，可能会启动应用");
            console.log("  ⊘ stop_app - 需要KILL_BACKGROUND_PROCESSES权限\n");
            results.push({ tool: 'modify_system_setting', result: { success: null, message: '未测试（避免修改系统）' } });
            results.push({ tool: 'install_app', result: { success: null, message: '未测试（需要APK文件）' } });
            results.push({ tool: 'uninstall_app', result: { success: null, message: '未测试（避免卸载应用）' } });
            results.push({ tool: 'start_app', result: { success: null, message: '未测试（避免启动应用）' } });
            results.push({ tool: 'stop_app', result: { success: null, message: '未测试（避免停止应用）' } });
            console.log("=== System Tools 测试完成 ===\n");
            console.log("测试结果汇总:");
            results.forEach((r, i) => {
                const status = r.result.success === true ? '✓' : r.result.success === false ? '✗' : '⊘';
                console.log(`${i + 1}. ${status} ${r.tool}: ${r.result.message}`);
            });
            const successCount = results.filter(r => r.result.success === true).length;
            const failCount = results.filter(r => r.result.success === false).length;
            const skipCount = results.filter(r => r.result.success === null).length;
            console.log(`\n总计: ${successCount} 成功, ${failCount} 失败, ${skipCount} 跳过`);
            complete({
                success: true,
                message: "系统工具全面测试完成",
                data: {
                    results,
                    summary: {
                        total: results.length,
                        success: successCount,
                        failed: failCount,
                        skipped: skipCount
                    }
                }
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
        get_system_setting: (params) => wrapToolExecution(get_system_setting, params),
        modify_system_setting: (params) => wrapToolExecution(modify_system_setting, params),
        install_app: (params) => wrapToolExecution(install_app, params),
        uninstall_app: (params) => wrapToolExecution(uninstall_app, params),
        list_installed_apps: (params) => wrapToolExecution(list_installed_apps, params),
        start_app: (params) => wrapToolExecution(start_app, params),
        stop_app: (params) => wrapToolExecution(stop_app, params),
        get_notifications: (params) => wrapToolExecution(get_notifications, params),
        get_device_location: (params) => wrapToolExecution(get_device_location, params),
        get_device_info: (params) => wrapToolExecution(get_device_info, params),
        main,
    };
})();
exports.get_system_setting = SystemTools.get_system_setting;
exports.modify_system_setting = SystemTools.modify_system_setting;
exports.install_app = SystemTools.install_app;
exports.uninstall_app = SystemTools.uninstall_app;
exports.list_installed_apps = SystemTools.list_installed_apps;
exports.start_app = SystemTools.start_app;
exports.stop_app = SystemTools.stop_app;
exports.get_notifications = SystemTools.get_notifications;
exports.get_device_location = SystemTools.get_device_location;
exports.get_device_info = SystemTools.get_device_info;
exports.main = SystemTools.main;
