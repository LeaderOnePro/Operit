"use strict";
/**
 * MCP TCP Bridge
 *
 * Creates a bridge that connects STDIO-based MCP servers to TCP clients
 */
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
const net = __importStar(require("net"));
const uuid_1 = require("uuid");
const fs = __importStar(require("fs"));
const path = __importStar(require("path"));
const os = __importStar(require("os"));
/**
 * MCP Bridge class
 */
class McpBridge {
    constructor(config = {}) {
        this.server = null;
        // 统一的服务客户端映射 (本地和远程都使用 MCPClient)
        this.serviceClients = new Map();
        this.mcpToolsMap = new Map();
        this.serviceReadyMap = new Map();
        // 服务注册表 (纯内存)
        this.serviceRegistry = new Map();
        // 活跃连接
        this.activeConnections = new Set();
        // 请求跟踪
        this.pendingRequests = new Map();
        // 请求超时(毫秒)
        this.REQUEST_TIMEOUT = 180000; // 180秒超时 (3分钟)
        // 服务错误记录
        this.mcpErrors = new Map();
        // 重启跟踪
        this.restartAttempts = new Map();
        this.MAX_RESTART_ATTEMPTS = 5; // 最多重启5次
        this.RESTART_DELAY_MS = 5000; // 基础重启延迟5秒
        // 默认配置
        this.config = {
            port: 8752,
            host: '127.0.0.1',
            mcpCommand: 'node',
            mcpArgs: ['../your-mcp-server.js'],
            ...config
        };
        // 设置超时检查
        setInterval(() => this.checkRequestTimeouts(), 5000);
    }
    /**
     * 注册新的MCP服务
     */
    registerService(name, info) {
        if (!name || !info.type) {
            return false;
        }
        if (info.type === 'local' && !info.command) {
            return false;
        }
        if (info.type === 'remote') {
            if (!info.endpoint) {
                return false;
            }
        }
        const serviceInfo = {
            name,
            type: info.type,
            command: info.command,
            args: info.args || [],
            cwd: info.cwd,
            endpoint: info.endpoint,
            connectionType: info.connectionType || 'httpStream', // Default to httpStream
            description: info.description || `MCP Service: ${name}`,
            env: info.env || {},
            created: Date.now(),
            lastUsed: undefined
        };
        this.serviceRegistry.set(name, serviceInfo);
        return true;
    }
    /**
     * 注销MCP服务
     */
    unregisterService(name) {
        if (!this.serviceRegistry.has(name)) {
            return false;
        }
        this.serviceRegistry.delete(name);
        return true;
    }
    /**
     * 获取已注册MCP服务列表
     */
    getServiceList() {
        return Array.from(this.serviceRegistry.values());
    }
    /**
     * 检查服务是否激活 (运行中或已连接)
     */
    isServiceActive(serviceName) {
        const serviceInfo = this.serviceRegistry.get(serviceName);
        if (!serviceInfo) {
            return false;
        }
        // 统一检查：客户端是否存在
        return this.serviceClients.has(serviceName);
    }
    /**
     * 连接到远程MCP服务 (HTTP/SSE)
     */
    async connectToRemoteService(serviceName, endpoint, connectionType = 'httpStream') {
        if (this.isServiceActive(serviceName)) {
            console.log(`Service ${serviceName} is already connected`);
            return;
        }
        console.log(`Connecting to remote MCP service ${serviceName} at ${endpoint}`);
        try {
            const { MCPClient } = await import('mcp-client');
            const client = new MCPClient({
                name: `bridge-client-for-${serviceName}`,
                version: '1.0.0',
            });
            // Store client immediately to mark as "active"
            this.serviceClients.set(serviceName, client);
            this.serviceReadyMap.set(serviceName, false);
            this.mcpToolsMap.set(serviceName, []);
            await client.connect({
                type: connectionType,
                url: endpoint,
            });
            console.log(`Successfully connected to remote service ${serviceName}`);
            this.restartAttempts.set(serviceName, 0); // Reset restart attempts on successful connection
            // Fetch tools after connection
            await this.fetchMcpTools(serviceName);
        }
        catch (error) {
            console.error(`Failed to connect to remote service ${serviceName}: ${error instanceof Error ? error.message : String(error)}`);
            this.handleServiceClosure(serviceName); // Use the closure handler for reconnection logic
        }
    }
    /**
     * 处理服务连接关闭和重连 (本地和远程服务统一处理)
     */
    handleServiceClosure(serviceName) {
        console.log(`Service ${serviceName} connection closed or failed.`);
        this.serviceClients.delete(serviceName);
        this.serviceReadyMap.set(serviceName, false);
        const serviceInfo = this.serviceRegistry.get(serviceName);
        if (!serviceInfo) {
            return; // Service unregistered, don't reconnect
        }
        const attempts = (this.restartAttempts.get(serviceName) || 0) + 1;
        this.restartAttempts.set(serviceName, attempts);
        if (attempts > this.MAX_RESTART_ATTEMPTS) {
            console.error(`Service ${serviceName} has failed too many times. Will not reconnect again.`);
            return;
        }
        const reconnectDelay = this.RESTART_DELAY_MS * Math.pow(2, attempts - 1);
        console.log(`Attempting to reconnect to service ${serviceName} in ${reconnectDelay / 1000}s (attempt ${attempts})...`);
        setTimeout(() => {
            if (serviceInfo.type === 'remote') {
                this.connectToRemoteService(serviceName, serviceInfo.endpoint, serviceInfo.connectionType);
            }
            else if (serviceInfo.type === 'local') {
                this.startLocalService(serviceName, serviceInfo.command, serviceInfo.args, serviceInfo.env, serviceInfo.cwd);
            }
        }, reconnectDelay);
    }
    /**
     * 启动本地MCP服务 (使用官方 MCPClient 的 stdio 连接)
     */
    /**
     * 展开路径中的 ~ 符号为用户主目录
     */
    expandPath(filePath) {
        if (filePath.startsWith('~/') || filePath === '~') {
            return path.join(os.homedir(), filePath.slice(1));
        }
        return filePath;
    }
    async startLocalService(serviceName, command, args, env, cwd) {
        if (!command) {
            console.log(`[${serviceName}] No command specified, skipping startup`);
            return;
        }
        if (this.isServiceActive(serviceName)) {
            console.log(`[${serviceName}] Service is already running`);
            return;
        }
        try {
            // 展开 cwd 中的 ~ 路径
            let workingDir = cwd || path.join(os.homedir(), 'mcp_plugins', serviceName);
            workingDir = this.expandPath(workingDir);
            // 自动转换 npx 为 pnpm dlx（因为系统使用 pnpm）
            let actualCommand = command;
            let actualArgs = args;
            if (command === 'npx') {
                console.log(`[${serviceName}] Detected npx command, converting to pnpm dlx`);
                actualCommand = 'pnpm';
                // 过滤掉 npx 特有的参数（如 -y, --yes），pnpm dlx 不需要这些
                const filteredArgs = args.filter(arg => arg !== '-y' && arg !== '--yes');
                actualArgs = ['dlx', ...filteredArgs];
            }
            // 展开 command 中的 ~ 路径（支持如 ~/venv/bin/python 的路径）
            actualCommand = this.expandPath(actualCommand);
            console.log(`[${serviceName}] Starting local service: ${actualCommand} ${actualArgs.join(' ')} in ${workingDir}`);
            console.log(`[${serviceName}] Node.js version: ${process.version}`);
            // 确保工作目录存在
            if (!fs.existsSync(workingDir)) {
                console.log(`[${serviceName}] Working directory does not exist, creating: ${workingDir}`);
                fs.mkdirSync(workingDir, { recursive: true });
            }
            const { MCPClient } = await import('mcp-client');
            const client = new MCPClient({
                name: `bridge-client-for-${serviceName}`,
                version: '1.0.0',
            });
            // Store client immediately
            this.serviceClients.set(serviceName, client);
            this.serviceReadyMap.set(serviceName, false);
            this.mcpToolsMap.set(serviceName, []);
            this.mcpErrors.delete(serviceName);
            // 准备环境变量：合并 process.env 和用户指定的 env
            // 添加一些有用的环境变量来避免 npm 和 uv 缓存问题
            const mergedEnv = {
                ...process.env,
                ...env,
                // 如果用户没有指定这些，添加它们来避免 npm 问题
                ...(env?.npm_config_cache ? {} : { npm_config_cache: path.join(workingDir, '.npm-cache') }),
                ...(env?.npm_config_prefer_offline ? {} : { npm_config_prefer_offline: 'true' }),
                // 为 uvx/uv 添加环境变量以避免硬链接问题（在某些文件系统上会失败）
                ...(env?.UV_LINK_MODE ? {} : { UV_LINK_MODE: 'copy' }),
            };
            // 连接到本地 stdio 服务
            await client.connect({
                type: 'stdio',
                command: actualCommand,
                args: actualArgs,
                env: mergedEnv,
                cwd: workingDir
            });
            console.log(`[${serviceName}] Successfully connected to local service`);
            this.restartAttempts.set(serviceName, 0); // Reset restart attempts on successful connection
            // Fetch tools after connection
            await this.fetchMcpTools(serviceName);
            // Set up stability check
            setTimeout(() => {
                if (this.isServiceActive(serviceName)) {
                    console.log(`[${serviceName}] Service appears stable, resetting restart counter.`);
                    this.restartAttempts.set(serviceName, 0);
                }
            }, 60000);
        }
        catch (error) {
            console.error(`[${serviceName}] Failed to start local service: ${error instanceof Error ? error.message : String(error)}`);
            this.mcpErrors.set(serviceName, error instanceof Error ? error.message : String(error));
            this.handleServiceClosure(serviceName); // Trigger reconnection logic
        }
    }
    /**
     * 获取特定服务的MCP工具列表 (统一处理本地和远程服务)
     */
    async fetchMcpTools(serviceName) {
        if (!this.isServiceActive(serviceName)) {
            // 如果客户端不可用，设置空工具并标记为就绪
            this.mcpToolsMap.set(serviceName, []);
            this.serviceReadyMap.set(serviceName, true);
            console.log(`MCP service ${serviceName} marked ready with no tools (client unavailable)`);
            return;
        }
        try {
            const client = this.serviceClients.get(serviceName);
            if (client) {
                const tools = await client.getAllTools();
                this.mcpToolsMap.set(serviceName, tools);
                console.log(`MCP service ${serviceName} is ready with ${tools.length} tools`);
            }
        }
        catch (error) {
            console.error(`Error fetching tools for ${serviceName}: ${error instanceof Error ? error.message : String(error)}`);
            this.mcpToolsMap.set(serviceName, []);
        }
        finally {
            // 标记服务为就绪状态
            this.serviceReadyMap.set(serviceName, true);
        }
    }
    /**
     * 处理客户端MCP命令
     */
    handleMcpCommand(command, socket) {
        const { id, command: cmdType, params } = command;
        let response;
        try {
            switch (cmdType) {
                case 'ping':
                    // 健康检查(单个服务或所有服务)
                    const pingServiceName = params?.serviceName || params?.name;
                    if (pingServiceName) {
                        if (this.serviceRegistry.has(pingServiceName)) {
                            const serviceInfo = this.serviceRegistry.get(pingServiceName);
                            const isActive = this.isServiceActive(pingServiceName);
                            const isReady = this.serviceReadyMap.get(pingServiceName) || false;
                            response = {
                                id,
                                success: true,
                                result: {
                                    status: isActive ? "ok" : "registered_not_active",
                                    name: pingServiceName,
                                    type: serviceInfo?.type,
                                    description: serviceInfo?.description,
                                    timestamp: Date.now(),
                                    active: isActive,
                                    ready: isReady
                                }
                            };
                            // 更新最后使用时间
                            if (serviceInfo) {
                                serviceInfo.lastUsed = Date.now();
                            }
                        }
                        else {
                            response = {
                                id,
                                success: false,
                                error: {
                                    code: -32601,
                                    message: `Service '${pingServiceName}' not registered`
                                }
                            };
                        }
                    }
                    else {
                        // 普通bridge健康检查
                        const runningServices = [...this.serviceClients.keys()];
                        response = {
                            id,
                            success: true,
                            result: {
                                timestamp: Date.now(),
                                status: 'ok',
                                activeServices: runningServices,
                                serviceCount: runningServices.length
                            }
                        };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'status':
                    // bridge状态及所有运行服务
                    const activeServices = [...this.serviceClients.keys()];
                    const serviceStatus = {};
                    for (const name of activeServices) {
                        serviceStatus[name] = {
                            active: this.isServiceActive(name),
                            ready: this.serviceReadyMap.get(name) || false,
                            toolCount: (this.mcpToolsMap.get(name) || []).length,
                            type: this.serviceRegistry.get(name)?.type
                        };
                    }
                    response = {
                        id,
                        success: true,
                        result: {
                            activeServices: activeServices,
                            serviceCount: activeServices.length,
                            services: serviceStatus,
                            pendingRequests: this.pendingRequests.size,
                            activeConnections: this.activeConnections.size
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'listtools':
                    // 查询特定服务的可用工具列表
                    const serviceToList = params?.name;
                    if (serviceToList) {
                        if (!this.isServiceActive(serviceToList)) {
                            response = {
                                id,
                                success: false,
                                error: {
                                    code: -32603,
                                    message: `Service '${serviceToList}' not active`
                                }
                            };
                        }
                        else {
                            response = {
                                id,
                                success: true,
                                result: {
                                    tools: this.mcpToolsMap.get(serviceToList) || []
                                }
                            };
                        }
                    }
                    else {
                        // 未指定服务，列出所有服务的工具
                        const allTools = {};
                        for (const [name, tools] of this.mcpToolsMap.entries()) {
                            if (this.isServiceActive(name)) {
                                allTools[name] = tools;
                            }
                        }
                        response = {
                            id,
                            success: true,
                            result: {
                                serviceTools: allTools
                            }
                        };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'list':
                    // 列出已注册的MCP服务并附带运行状态
                    const services = this.getServiceList().map(service => {
                        return {
                            ...service,
                            active: this.isServiceActive(service.name),
                            ready: this.serviceReadyMap.get(service.name) || false,
                            toolCount: (this.mcpToolsMap.get(service.name) || []).length
                        };
                    });
                    response = {
                        id,
                        success: true,
                        result: { services }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'spawn':
                    // 启动新的MCP服务，不关闭其他服务
                    if (!params) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing parameters"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    const spawnServiceName = params.name;
                    let serviceCommand = params.command;
                    let serviceArgs = params.args || [];
                    let serviceEnv = params.env;
                    let serviceCwd = params.cwd;
                    // 优先从注册表查找服务信息
                    const serviceInfo = this.serviceRegistry.get(spawnServiceName);
                    // 如果未提供命令，但服务已注册，则使用注册表信息
                    if (serviceInfo) {
                        if (serviceInfo.type === 'local') {
                            this.startLocalService(spawnServiceName, serviceInfo.command, serviceInfo.args, serviceInfo.env, serviceInfo.cwd);
                        }
                        else if (serviceInfo.type === 'remote') {
                            this.connectToRemoteService(spawnServiceName, serviceInfo.endpoint, serviceInfo.connectionType);
                        }
                    }
                    else if (serviceCommand) {
                        // 如果服务未注册，但提供了command，则假定为本地服务并自动注册
                        this.registerService(spawnServiceName, {
                            type: 'local',
                            command: serviceCommand,
                            args: serviceArgs,
                            cwd: serviceCwd,
                            description: `Auto-registered service ${spawnServiceName}`,
                            env: serviceEnv,
                        });
                        console.log(`Auto-registered new service: ${spawnServiceName}`);
                        this.startLocalService(spawnServiceName, serviceCommand, serviceArgs, serviceEnv, serviceCwd);
                    }
                    else {
                        // 如果服务未注册且没有提供command，则无法启动
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: `Service '${spawnServiceName}' is not registered and no command provided.`
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    const finalServiceInfo = this.serviceRegistry.get(spawnServiceName);
                    response = {
                        id,
                        success: true,
                        result: {
                            status: "started",
                            name: spawnServiceName,
                            command: finalServiceInfo?.command || serviceCommand,
                            args: finalServiceInfo?.args || serviceArgs,
                            cwd: finalServiceInfo?.cwd || serviceCwd
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'shutdown':
                    // 关闭特定的MCP服务
                    const serviceToShutdown = params?.name;
                    if (!serviceToShutdown) {
                        // 未指定服务，返回错误
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameter: name"
                            }
                        };
                    }
                    else if (!this.isServiceActive(serviceToShutdown)) {
                        // 服务未运行
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: `Service '${serviceToShutdown}' not active`
                            }
                        };
                    }
                    else {
                        // 获取客户端并关闭（异步操作）
                        const client = this.serviceClients.get(serviceToShutdown);
                        if (client) {
                            console.log(`[${serviceToShutdown}] Closing MCP client...`);
                            // 异步关闭客户端，不等待完成
                            client.close().then(() => {
                                console.log(`[${serviceToShutdown}] MCP client closed successfully`);
                            }).catch((error) => {
                                console.error(`[${serviceToShutdown}] Error closing client: ${error.message}`);
                            });
                        }
                        // 完全注销服务，清理所有相关状态
                        this.serviceClients.delete(serviceToShutdown);
                        this.serviceReadyMap.delete(serviceToShutdown);
                        this.mcpToolsMap.delete(serviceToShutdown);
                        this.restartAttempts.delete(serviceToShutdown);
                        this.serviceRegistry.delete(serviceToShutdown); // 从注册表中移除，防止自动重连
                        console.log(`[${serviceToShutdown}] Service completely unregistered`);
                        response = {
                            id,
                            success: true,
                            result: {
                                status: "shutdown",
                                name: serviceToShutdown
                            }
                        };
                    }
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'register':
                    // 注册新的MCP服务
                    if (!params || !params.name || !params.type) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameters: name, type"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    if (params.type === 'local' && !params.command) {
                        response = {
                            id,
                            success: false,
                            error: { code: -32602, message: "Missing parameter 'command' for local service" }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    if (params.type === 'remote' && !params.endpoint) {
                        response = {
                            id,
                            success: false,
                            error: { code: -32602, message: "Missing 'endpoint' for remote service" }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    const registered = this.registerService(params.name, {
                        type: params.type,
                        command: params.command,
                        args: params.args || [],
                        cwd: params.cwd,
                        description: params.description,
                        env: params.env,
                        endpoint: params.endpoint,
                        connectionType: params.connectionType,
                    });
                    response = {
                        id,
                        success: registered,
                        result: registered ? {
                            status: 'registered',
                            name: params.name
                        } : undefined,
                        error: !registered ? {
                            code: -32602,
                            message: "Failed to register service"
                        } : undefined
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'unregister':
                    // 注销MCP服务
                    if (!params || !params.name) {
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: "Missing required parameter: name"
                            }
                        };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    const serviceNameToUnregister = params.name;
                    if (!this.serviceRegistry.has(serviceNameToUnregister)) {
                        response = { id, success: false, error: { code: -32602, message: `Service '${serviceNameToUnregister}' not registered` } };
                        socket.write(JSON.stringify(response) + '\n');
                        break;
                    }
                    // 如果运行中，先关闭
                    if (this.isServiceActive(serviceNameToUnregister)) {
                        const serviceInfoToShutdown = this.serviceRegistry.get(serviceNameToUnregister);
                        if (serviceInfoToShutdown.type === 'local') {
                            this.serviceClients.delete(serviceNameToUnregister);
                        }
                        else if (serviceInfoToShutdown.type === 'remote') {
                            const client = this.serviceClients.get(serviceNameToUnregister);
                            client?.close();
                            this.serviceClients.delete(serviceNameToUnregister);
                        }
                    }
                    const unregistered = this.unregisterService(serviceNameToUnregister);
                    response = {
                        id,
                        success: unregistered,
                        result: unregistered ? {
                            status: 'unregistered',
                            name: serviceNameToUnregister
                        } : undefined,
                        error: !unregistered ? {
                            code: -32602,
                            message: `Service '${serviceNameToUnregister}' does not exist`
                        } : undefined
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                case 'toolcall':
                    // 调用工具
                    this.handleToolCall(command, socket);
                    break;
                case 'reset':
                    // 重置所有服务：关闭所有客户端，清空注册表
                    console.log('Resetting bridge: closing all services and clearing registry...');
                    // 关闭所有活跃的服务客户端
                    for (const [name, client] of this.serviceClients.entries()) {
                        try {
                            console.log(`Closing service: ${name}`);
                            client.close();
                        }
                        catch (error) {
                            console.error(`Error closing service ${name}: ${error instanceof Error ? error.message : String(error)}`);
                        }
                    }
                    // 清空所有映射和状态
                    this.serviceClients.clear();
                    this.mcpToolsMap.clear();
                    this.serviceReadyMap.clear();
                    this.mcpErrors.clear();
                    this.restartAttempts.clear();
                    // 清空服务注册表
                    this.serviceRegistry.clear();
                    // 清空待处理的请求
                    this.pendingRequests.clear();
                    console.log('Bridge reset complete: all services closed, registry cleared');
                    response = {
                        id,
                        success: true,
                        result: {
                            status: 'reset',
                            message: 'All services closed and registry cleared'
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
                    break;
                default:
                    // 未知命令
                    response = {
                        id,
                        success: false,
                        error: {
                            code: -32601,
                            message: `Unknown command: ${cmdType}`
                        }
                    };
                    socket.write(JSON.stringify(response) + '\n');
            }
        }
        catch (error) {
            // 通用错误处理
            const errorResponse = {
                id,
                success: false,
                error: {
                    code: -32603,
                    message: `Internal server error: ${error instanceof Error ? error.message : String(error)}`
                }
            };
            socket.write(JSON.stringify(errorResponse) + '\n');
        }
    }
    /**
     * 处理工具调用请求
     */
    async handleToolCall(command, socket) {
        const { id, params } = command;
        const { method, params: methodParams, name: requestedServiceName } = params || {};
        // 确定使用哪个服务
        const serviceName = requestedServiceName || this.serviceClients.keys().next().value;
        if (!serviceName) {
            console.error(`Cannot handle tool call: No service specified and no default available`);
            const response = {
                id,
                success: false,
                error: {
                    code: -32602,
                    message: 'No service specified and no default available'
                }
            };
            socket.write(JSON.stringify(response) + '\n');
            return;
        }
        const serviceInfo = this.serviceRegistry.get(serviceName);
        if (!serviceInfo) {
            // This case should ideally not happen if isServiceActive passed
            const response = {
                id,
                success: false,
                error: {
                    code: -32603,
                    message: `Service '${serviceName}' not registered`
                }
            };
            socket.write(JSON.stringify(response) + '\n');
            return;
        }
        try {
            // 记录请求
            this.pendingRequests.set(id, {
                id,
                socket,
                timestamp: Date.now()
            });
            let toolCallResult;
            let toolCallError;
            if (serviceInfo.type === 'local') {
                // --- LOCAL SERVICE TOOLCALL (existing logic) ---
                const toolCallId = params.id || (0, uuid_1.v4)();
                const toolCallRequest = {
                    jsonrpc: '2.0',
                    id: toolCallId,
                    method: 'tools/call',
                    params: {
                        name: params.method,
                        arguments: params.params || {}
                    }
                };
                // Update pending request with toolCallId for response mapping
                this.pendingRequests.get(id).toolCallId = toolCallId;
                // this.toolResponseMapping.set(toolCallId, id); // No longer needed
                // this.toolCallServiceMap.set(toolCallId, serviceName); // No longer needed
                const client = this.serviceClients.get(serviceName);
                if (!client) {
                    throw new Error(`MCPClient for service ${serviceName} not found`);
                }
                const result = await client.callTool({
                    name: method,
                    arguments: methodParams || {},
                });
                // The mcp-client result is the "content" part of the MCP response
                toolCallResult = { content: result.content };
                toolCallError = result.isError ? { code: -32000, message: result.content[0]?.text || "Remote tool error" } : undefined;
            }
            else if (serviceInfo.type === 'remote') {
                // --- REMOTE SERVICE TOOLCALL (new logic) ---
                const client = this.serviceClients.get(serviceName);
                if (!client) {
                    throw new Error(`MCPClient for service ${serviceName} not found`);
                }
                const result = await client.callTool({
                    name: method,
                    arguments: methodParams || {},
                });
                // The mcp-client result is the "content" part of the MCP response
                toolCallResult = { content: result.content };
                toolCallError = result.isError ? { code: -32000, message: result.content[0]?.text || "Remote tool error" } : undefined;
            }
            // --- SEND RESPONSE (统一处理本地和远程服务) ---
            const response = {
                id,
                success: !toolCallError,
                result: toolCallResult,
                error: toolCallError
            };
            socket.write(JSON.stringify(response) + '\n');
            this.pendingRequests.delete(id);
        }
        catch (error) {
            console.error(`Error handling tool call for ${serviceName}: ${error instanceof Error ? error.message : String(error)}`);
            // 发送错误响应
            const response = {
                id,
                success: false,
                error: {
                    code: -32603,
                    message: `Internal error: ${error instanceof Error ? error.message : String(error)}`
                }
            };
            socket.write(JSON.stringify(response) + '\n');
            // 清理
            const pendingRequest = this.pendingRequests.get(id);
            if (pendingRequest?.toolCallId) {
                // this.toolResponseMapping.delete(pendingRequest.toolCallId); // No longer needed
                // this.toolCallServiceMap.delete(pendingRequest.toolCallId); // No longer needed
            }
            this.pendingRequests.delete(id);
        }
    }
    /**
     * 检查请求超时
     */
    checkRequestTimeouts() {
        const now = Date.now();
        for (const [requestId, request] of this.pendingRequests.entries()) {
            if (now - request.timestamp > this.REQUEST_TIMEOUT) {
                console.log(`Request timeout: ${requestId}`);
                // 发送超时响应
                const response = {
                    id: requestId,
                    success: false,
                    error: {
                        code: -32603,
                        message: "Request timeout"
                    }
                };
                request.socket.write(JSON.stringify(response) + '\n');
                // 清理
                this.pendingRequests.delete(requestId);
                if (request.toolCallId) {
                    // this.toolResponseMapping.delete(request.toolCallId); // No longer needed
                    // this.toolCallServiceMap.delete(request.toolCallId); // No longer needed
                }
            }
        }
    }
    /**
     * 启动TCP服务器
     */
    start() {
        // 创建TCP服务器 - 默认不启动任何MCP进程
        this.server = net.createServer((socket) => {
            console.log(`New client connection: ${socket.remoteAddress}:${socket.remotePort}`);
            this.activeConnections.add(socket);
            // 添加socket超时以防止客户端挂起
            // 设置为请求超时的 2 倍，确保有足够时间完成工具调用
            socket.setTimeout(120000); // 120秒超时 (REQUEST_TIMEOUT * 2)
            socket.on('timeout', () => {
                console.log(`Socket timeout: ${socket.remoteAddress}:${socket.remotePort}`);
                socket.end();
                this.activeConnections.delete(socket);
            });
            // 处理来自客户端的数据
            socket.on('data', (data) => {
                const message = data.toString().trim();
                try {
                    // 解析命令
                    const command = JSON.parse(message);
                    // 确保命令有ID
                    if (!command.id) {
                        command.id = (0, uuid_1.v4)();
                    }
                    // 处理命令
                    if (command.command) {
                        this.handleMcpCommand(command, socket);
                    }
                    else {
                        // 非桥接命令，无默认服务转发
                        socket.write(JSON.stringify({
                            jsonrpc: '2.0',
                            id: this.extractId(message),
                            error: {
                                code: -32600,
                                message: 'Invalid request: no service specified'
                            }
                        }) + '\n');
                    }
                }
                catch (e) {
                    console.error(`Failed to parse client message: ${e}`);
                    // 发送错误响应
                    socket.write(JSON.stringify({
                        jsonrpc: '2.0',
                        id: null,
                        error: {
                            code: -32700,
                            message: `Invalid JSON: ${e}`
                        }
                    }) + '\n');
                }
            });
            // 处理客户端断开连接
            socket.on('close', () => {
                console.log(`Client disconnected: ${socket.remoteAddress}:${socket.remotePort}`);
                this.activeConnections.delete(socket);
                // 清理此连接的待处理请求
                for (const [requestId, request] of this.pendingRequests.entries()) {
                    if (request.socket === socket) {
                        const toolCallId = request.toolCallId;
                        this.pendingRequests.delete(requestId);
                        if (toolCallId) {
                            // this.toolResponseMapping.delete(toolCallId); // No longer needed
                            // this.toolCallServiceMap.delete(toolCallId); // No longer needed
                        }
                    }
                }
            });
            // 处理客户端错误
            socket.on('error', (err) => {
                console.error(`Client error: ${err.message}`);
                this.activeConnections.delete(socket);
            });
        });
        // 启动TCP服务器
        this.server.listen(this.config.port, this.config.host, () => {
            console.log(`TCP bridge server running on ${this.config.host}:${this.config.port}`);
        });
        // 处理服务器错误
        this.server.on('error', (err) => {
            console.error(`Server error: ${err.message}`);
        });
        // 处理进程信号
        process.on('SIGINT', () => this.shutdown());
        process.on('SIGTERM', () => this.shutdown());
    }
    /**
     * 关闭桥接器
     */
    shutdown() {
        console.log('Shutting down bridge...');
        // 关闭所有客户端连接
        for (const socket of this.activeConnections) {
            socket.end();
        }
        // 关闭服务器
        if (this.server) {
            this.server.close();
        }
        // 终止所有MCP进程
        for (const [name, client] of this.serviceClients.entries()) {
            console.log(`Closing MCP client: ${name}`);
            client.close();
        }
        this.serviceClients.clear();
        console.log('Bridge shut down');
        process.exit(0);
    }
    /**
     * 从JSON-RPC请求中提取ID
     */
    extractId(request) {
        try {
            const json = JSON.parse(request);
            return json.id || null;
        }
        catch (e) {
            return null;
        }
    }
}
// If running this script directly, create and start bridge
if (require.main === module) {
    // Parse config from command line args
    const args = process.argv.slice(2);
    const port = parseInt(args[0]) || 8752;
    const mcpCommand = args[1] || 'node';
    const mcpArgs = args.slice(2);
    const bridge = new McpBridge({
        port,
        mcpCommand,
        mcpArgs: mcpArgs.length > 0 ? mcpArgs : undefined
    });
    bridge.start();
}
// Export bridge class for use by other modules
exports.default = McpBridge;
//# sourceMappingURL=index.js.map