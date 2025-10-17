package com.ai.assistance.operit.core.tools.javascript

/** JavaScript Tools 对象定义 提供了一组便捷的工具调用方法，用于在JavaScript脚本中调用Android工具 */
fun getJsToolsDefinition(): String {
    return """
        // 工具调用的便捷方法
        var Tools = {
            // 文件系统操作
            Files: {
                list: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("list_files", params);
                },
                read: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("read_file_full", params);
                },
                write: (path, content, append, environment) => {
                    const params = { path, content };
                    if (append !== undefined) params.append = append ? "true" : "false";
                    if (environment) params.environment = environment;
                    return toolCall("write_file", params);
                },
                writeBinary: (path, base64Content, environment) => {
                    const params = { path, base64Content };
                    if (environment) params.environment = environment;
                    return toolCall("write_file_binary", params);
                },
                deleteFile: (path, recursive, environment) => {
                    const params = { path };
                    if (recursive !== undefined) params.recursive = recursive ? "true" : "false";
                    if (environment) params.environment = environment;
                    return toolCall("delete_file", params);
                },
                exists: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("file_exists", params);
                },
                move: (source, destination, environment) => {
                    const params = { source, destination };
                    if (environment) params.environment = environment;
                    return toolCall("move_file", params);
                },
                copy: (source, destination, recursive, environment) => {
                    const params = { source, destination };
                    if (recursive !== undefined) params.recursive = recursive ? "true" : "false";
                    if (environment) params.environment = environment;
                    return toolCall("copy_file", params);
                },
                mkdir: (path, create_parents, environment) => {
                    const params = { path };
                    if (create_parents !== undefined) params.create_parents = create_parents ? "true" : "false";
                    if (environment) params.environment = environment;
                    return toolCall("make_directory", params);
                },
                find: (path, pattern, options = {}, environment) => {
                    const params = { path, pattern, ...options };
                    if (environment) params.environment = environment;
                    return toolCall("find_files", params);
                },
                grep: (path, pattern, options = {}) => {
                    const params = { path, pattern, ...options };
                    // environment can be included in options
                    return toolCall("grep_code", params);
                },
                info: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("file_info", params);
                },
                // 智能应用文件绑定
                apply: (path, content, environment) => {
                    const params = { path, content };
                    if (environment) params.environment = environment;
                    return toolCall("apply_file", params);
                },
                zip: (source, destination, environment) => {
                    const params = { source, destination };
                    if (environment) params.environment = environment;
                    return toolCall("zip_files", params);
                },
                unzip: (source, destination, environment) => {
                    const params = { source, destination };
                    if (environment) params.environment = environment;
                    return toolCall("unzip_files", params);
                },
                open: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("open_file", params);
                },
                share: (path, title, environment) => {
                    const params = { path };
                    if (title) params.title = title;
                    if (environment) params.environment = environment;
                    return toolCall("share_file", params);
                },
                download: (url, destination, environment) => {
                    const params = { url, destination };
                    if (environment) params.environment = environment;
                    return toolCall("download_file", params);
                },
            },
            // 网络操作
            Net: {
                httpGet: (url) => toolCall("http_request", { url, method: "GET" }),
                httpPost: (url, body) => {
                    const params = { url, method: "POST", body };
                    if (typeof body === 'object') {
                        params.body = JSON.stringify(body);
                    }
                    return toolCall("http_request", params);
                },
                visit: (url) => toolCall("visit_web", { url }),
                // 新增增强版HTTP请求
                http: (options) => {
                    const params = { ...options };
                    if (params.body !== undefined && typeof params.body === 'object') {
                        params.body = JSON.stringify(params.body);
                    }
                    if (params.headers !== undefined && typeof params.headers === 'object') {
                        params.headers = JSON.stringify(params.headers);
                    }
                    return toolCall("http_request", params);
                },
                // 新增文件上传
                uploadFile: (options) => {
                    const params = {
                        ...options,
                        files: JSON.stringify(options.files || []),
                        form_data: JSON.stringify(options.form_data || {})
                    };
                    if (options.headers !== undefined && typeof options.headers === 'object') {
                        params.headers = JSON.stringify(options.headers);
                    }
                    return toolCall("multipart_request", params);
                },
                // 新增Cookie管理
                cookies: {
                    get: (domain) => toolCall("manage_cookies", { action: "get", domain }),
                    set: (domain, cookies) => toolCall("manage_cookies", { action: "set", domain, cookies }),
                    clear: (domain) => toolCall("manage_cookies", { action: "clear", domain })
                }
            },
            // 系统操作
            System: {
                sleep: (milliseconds) => toolCall("sleep", { duration_ms: parseInt(milliseconds) }),
                getSetting: (setting, namespace) => toolCall("get_system_setting", { setting, namespace }),
                setSetting: (setting, value, namespace) => toolCall("modify_system_setting", { setting, value, namespace }),
                getDeviceInfo: () => toolCall("device_info"),
                // 使用工具包
                usePackage: (packageName) => toolCall("use_package", { package_name: packageName }),
                // 安装应用
                installApp: (path) => toolCall("install_app", { path }),
                // 卸载应用
                uninstallApp: (packageName) => toolCall("uninstall_app", { package_name: packageName }),
                startApp: (packageName, activity) => {
                    const params = { package_name: packageName };
                    if (activity) params.activity = activity;
                    return toolCall("start_app", params);
                },
                stopApp: (packageName) => toolCall("stop_app", { package_name: packageName }),
                listApps: (includeSystem) => toolCall("list_installed_apps", { include_system: !!includeSystem }),
                // 获取设备通知
                getNotifications: (limit = 10, includeOngoing = false) => 
                    toolCall("get_notifications", { limit: parseInt(limit), include_ongoing: !!includeOngoing }),
                // 获取设备位置
                getLocation: (highAccuracy = false, timeout = 10) => 
                    toolCall("get_device_location", { high_accuracy: !!highAccuracy, timeout: parseInt(timeout) }),
                shell: (command) => toolCall("execute_shell", { command }),
                // 执行终端命令 - 一次性收集输出
                terminal: {
                    create: (sessionName) => toolCall("create_terminal_session", { session_name: sessionName }),
                    exec: (sessionId, command, timeoutMs) => {
                        const params = { session_id: sessionId, command };
                        if (timeoutMs) params.timeout_ms = timeoutMs;
                        return toolCall("execute_in_terminal_session", params);
                    },
                    close: (sessionId) => toolCall("close_terminal_session", { session_id: sessionId })
                },
                // 执行Intent
                intent: (options = {}) => {
                    return toolCall("execute_intent", options);
                }
            },
            // UI操作
            UI: {
                getPageInfo: () => toolCall("get_page_info"),
                tap: (x, y) => toolCall("tap", { x, y }),
                // 增强的clickElement方法，支持多种参数类型
                clickElement: function(param1, param2, param3) {
                    // 根据参数类型和数量判断调用方式
                    if (typeof param1 === 'object') {
                        // 如果第一个参数是对象，直接传递参数对象
                        return toolCall("click_element", param1);
                    } else if (arguments.length === 1) {
                        // 单参数，假定为resourceId
                        if (param1.startsWith('[') && param1.includes('][')) {
                            // 参数看起来像bounds格式 [x,y][x,y]
                            return toolCall("click_element", { bounds: param1 });
                        }
                        return toolCall("click_element", { resourceId: param1 });
                    } else if (arguments.length === 2) {
                        // 两个参数，假定为(resourceId, index)或(className, index)
                        if (param1 === 'resourceId') {
                            return toolCall("click_element", { resourceId: param2 });
                        } else if (param1 === 'className') {
                            return toolCall("click_element", { className: param2 });
                        } else if (param1 === 'bounds') {
                            return toolCall("click_element", { bounds: param2 });
                        } else {
                            return toolCall("click_element", { resourceId: param1, index: param2 });
                        }
                    } else if (arguments.length === 3) {
                        // 三个参数，假定为(type, value, index)
                        if (param1 === 'resourceId') {
                            return toolCall("click_element", { resourceId: param2, index: param3 });
                        } else if (param1 === 'className') {
                            return toolCall("click_element", { className: param2, index: param3 });
                        } else {
                            return toolCall("click_element", { resourceId: param1, className: param2, index: param3 });
                        }
                    }
                    // 默认情况
                    return toolCall("click_element", { resourceId: param1 });
                },

                setText: (text, resourceId) => {
                    const params = { text };
                    if (resourceId) params.resourceId = resourceId;
                    return toolCall("set_input_text", params);
                },
                swipe: (startX, startY, endX, endY, duration) => {
                    const params = { 
                        start_x: startX, 
                        start_y: startY, 
                        end_x: endX, 
                        end_y: endY 
                    };
                    if (duration) params.duration = duration;
                    return toolCall("swipe", params);
                },
                pressKey: (keyCode) => toolCall("press_key", { key_code: keyCode }),
            },
            // 知识查询
            Query: {
                // 查询问题库
                knowledge: (query) => toolCall("query_memory", { query })
            },
            // 计算功能
            calc: (expression) => toolCall("calculate", { expression }),
            
            // FFmpeg工具
            FFmpeg: {
                // 执行自定义FFmpeg命令
                execute: (command) => toolCall("ffmpeg_execute", { command }),
                
                // 获取FFmpeg系统信息
                info: () => toolCall("ffmpeg_info"),
                
                // 转换视频文件
                convert: (inputPath, outputPath, options = {}) => {
                    const params = {
                        input_path: inputPath,
                        output_path: outputPath,
                        ...options
                    };
                    return toolCall("ffmpeg_convert", params);
                }
            }
        };
    """.trimIndent()
}
