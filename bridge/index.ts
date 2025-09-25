/**
 * MCP TCP Bridge
 * 
 * Creates a bridge that connects STDIO-based MCP servers to TCP clients
 */

import * as net from 'net';
import { spawn, ChildProcessWithoutNullStreams } from 'child_process';
import * as readline from 'readline';
import { v4 as uuidv4 } from 'uuid';
import * as fs from 'fs';
import * as path from 'path';
import * as http from 'http';
import * as os from 'os';

// Configuration
interface BridgeConfig {
    port: number;
    host: string;
    mcpCommand: string;
    mcpArgs: string[];
    registryPath?: string;
    env?: Record<string, string>;
}

// MCP service registration info
interface McpServiceInfo {
    name: string;
    description: string;
    type: 'local' | 'remote';

    // For local services
    command?: string;
    args?: string[];
    cwd?: string;
    env?: Record<string, string>;

    // For remote services
    endpoint?: string;
    connectionType?: 'httpStream' | 'sse';

    created: number;
    lastUsed?: number;
}

// Command types
type McpCommandType = 'spawn' | 'shutdown' | 'listtools' | 'toolcall' | 'ping' | 'status' | 'list' | 'register' | 'unregister';

// Command interface
interface McpCommand {
    command: McpCommandType;
    id: string;
    params?: any;
}

// Response interface
interface McpResponse {
    id: string;
    success: boolean;
    result?: any;
    error?: {
        code: number;
        message: string;
        data?: any;
    };
}

// Tool call request
interface ToolCallRequest {
    jsonrpc: string;
    id: string;
    method: string;
    params: any;
}

// Tracking pending requests
interface PendingRequest {
    id: string;
    socket: net.Socket;
    timestamp: number;
    toolCallId?: string;
}

/**
 * MCP Bridge class
 */
class McpBridge {
    private config: BridgeConfig;
    private server: net.Server | null = null;

    // 服务进程/连接映射
    private mcpProcesses: Map<string, ChildProcessWithoutNullStreams> = new Map();
    private remoteServiceClients: Map<string, import('mcp-client').MCPClient> = new Map();
    private mcpToolsMap: Map<string, any[]> = new Map();
    private serviceReadyMap: Map<string, boolean> = new Map();

    // 服务注册表 (纯内存)
    private serviceRegistry: Map<string, McpServiceInfo> = new Map();

    // 活跃连接
    private activeConnections: Set<net.Socket> = new Set();

    // 请求跟踪
    private pendingRequests: Map<string, PendingRequest> = new Map();
    private toolResponseMapping: Map<string, string> = new Map();
    private toolCallServiceMap: Map<string, string> = new Map();

    // 请求超时(毫秒)
    private readonly REQUEST_TIMEOUT = 60000; // 60秒超时

    // 服务错误记录
    private mcpErrors: Map<string, string> = new Map();

    // 重启跟踪
    private restartAttempts: Map<string, number> = new Map();
    private readonly MAX_RESTART_ATTEMPTS = 5; // 最多重启5次
    private readonly RESTART_DELAY_MS = 5000; // 基础重启延迟5秒

    constructor(config: Partial<BridgeConfig> = {}) {
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
    private registerService(name: string, info: Partial<McpServiceInfo>): boolean {
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

        const serviceInfo: McpServiceInfo = {
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
    private unregisterService(name: string): boolean {
        if (!this.serviceRegistry.has(name)) {
            return false;
        }

        this.serviceRegistry.delete(name);
        return true;
    }

    /**
     * 获取已注册MCP服务列表
     */
    private getServiceList(): McpServiceInfo[] {
        return Array.from(this.serviceRegistry.values());
    }

    /**
     * 检查服务是否激活 (运行中或已连接)
     */
    private isServiceActive(serviceName: string): boolean {
        const serviceInfo = this.serviceRegistry.get(serviceName);
        if (!serviceInfo) {
            return false;
        }

        if (serviceInfo.type === 'local') {
            return this.mcpProcesses.has(serviceName) &&
                !this.mcpProcesses.get(serviceName)?.stdin.destroyed;
        } else if (serviceInfo.type === 'remote') {
            const client = this.remoteServiceClients.get(serviceName);
            return !!client; // MCPClient doesn't expose a public "isConnected" property easily. We'll rely on its existence.
        }
        return false;
    }

    /**
     * 连接到远程MCP服务
     */
    private async connectToRemoteService(serviceName: string, endpoint: string, connectionType: 'httpStream' | 'sse' = 'httpStream'): Promise<void> {
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
            this.remoteServiceClients.set(serviceName, client);
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

        } catch (error) {
            console.error(`Failed to connect to remote service ${serviceName}: ${error instanceof Error ? error.message : String(error)}`);
            this.handleRemoteClosure(serviceName); // Use the closure handler for reconnection logic
        }
    }

    /**
     * 处理远程连接关闭和重连
     */
    private handleRemoteClosure(serviceName: string): void {
        console.log(`Remote service ${serviceName} connection closed or failed.`);
        this.remoteServiceClients.delete(serviceName);
        this.serviceReadyMap.set(serviceName, false);

        const serviceInfo = this.serviceRegistry.get(serviceName);
        if (serviceInfo && serviceInfo.type === 'remote' && serviceInfo.endpoint) { // Only reconnect if it's still registered
            const attempts = (this.restartAttempts.get(serviceName) || 0) + 1;
            this.restartAttempts.set(serviceName, attempts);

            if (attempts > this.MAX_RESTART_ATTEMPTS) {
                console.error(`Service ${serviceName} has disconnected too many times. Will not reconnect again.`);
                return;
            }

            const reconnectDelay = this.RESTART_DELAY_MS * Math.pow(2, attempts - 1);
            console.log(`Attempting to reconnect to service ${serviceName} in ${reconnectDelay / 1000}s (attempt ${attempts})...`);
            setTimeout(() => {
                this.connectToRemoteService(serviceName, serviceInfo.endpoint!, serviceInfo.connectionType);
            }, reconnectDelay);
        }
    }


    /**
     * 启动特定服务的子进程
     */
    private startMcpProcess(
        serviceName: string,
        command: string,
        args: string[],
        env?: Record<string, string>,
        cwd?: string
    ): void {
        if (!command) {
            console.log(`[${serviceName}] No command specified, skipping startup`);
            return;
        }

        if (this.isServiceActive(serviceName)) {
            console.log(`[${serviceName}] Service is already running`);
            return;
        }

        try {
            const workingDir = path.join(os.homedir(), 'mcp_plugins', serviceName);
            console.log(`[${serviceName}] Starting process: ${command} ${args.join(' ')} in ${workingDir}`);

            const mcpProcess = spawn(command, args, {
                stdio: ['pipe', 'pipe', 'pipe'],
                cwd: workingDir,
                env: {
                    ...process.env,
                    ...env
                }
            });

            this.mcpProcesses.set(serviceName, mcpProcess);
            this.mcpToolsMap.set(serviceName, this.mcpToolsMap.get(serviceName) || []);
            this.serviceReadyMap.set(serviceName, false);
            this.mcpErrors.delete(serviceName); // Clear previous errors

            mcpProcess.stdout?.on('data', (data: Buffer) => {
                this.handleMcpResponse(data, serviceName);
            });

            mcpProcess.stderr?.on('data', (data: Buffer) => {
                const errorText = data.toString().trim();
                console.error(`[${serviceName}] STDERR: ${errorText}`);
                const currentErrors = this.mcpErrors.get(serviceName) || "";
                this.mcpErrors.set(serviceName, `${currentErrors}\n${errorText}`.trim());
            });

            mcpProcess.on('error', (err) => {
                console.error(`[${serviceName}] Spawn Error: ${err.message}`);
                this.mcpErrors.set(serviceName, `Spawn Error: ${err.message}`);
            });

            mcpProcess.on('close', (code: number, signal: string) => {
                console.log(`[${serviceName}] Process exited with code: ${code}, signal: ${signal}`);
                const errorOutput = this.mcpErrors.get(serviceName);
                if (code !== 0 && errorOutput) {
                    console.error(`[${serviceName}] Process exited with an error. Last known error output:\n${errorOutput}`);
                }

                this.mcpProcesses.delete(serviceName);
                this.serviceReadyMap.set(serviceName, false);

                const serviceInfo = this.serviceRegistry.get(serviceName);
                if (serviceInfo && code !== 0) { // Only restart on non-zero exit codes
                    const attempts = (this.restartAttempts.get(serviceName) || 0) + 1;
                    this.restartAttempts.set(serviceName, attempts);

                    if (attempts > this.MAX_RESTART_ATTEMPTS) {
                        console.error(`[${serviceName}] Service has crashed too many times. Will not restart again.`);
                        return;
                    }

                    const restartDelay = this.RESTART_DELAY_MS * Math.pow(2, attempts - 1);
                    console.log(`[${serviceName}] Attempting to restart in ${restartDelay / 1000}s (attempt ${attempts})...`);
                    setTimeout(() => {
                        this.startMcpProcess(
                            serviceName,
                            serviceInfo.command!,
                            serviceInfo.args!,
                            serviceInfo.env,
                            serviceInfo.cwd
                        );
                    }, restartDelay);
                } else {
                    console.log(`[${serviceName}] Not restarting (exit code 0 or service unregistered).`);
                }
            });

            setTimeout(() => this.fetchMcpTools(serviceName), 1000);

            setTimeout(() => {
                if (this.isServiceActive(serviceName)) {
                    console.log(`[${serviceName}] Service appears stable, resetting restart counter.`);
                    this.restartAttempts.set(serviceName, 0);
                }
            }, 60000);
        } catch (error) {
            console.error(`[${serviceName}] Failed to start process: ${error instanceof Error ? error.message : String(error)}`);
            this.serviceReadyMap.set(serviceName, true); // Mark as ready to prevent deadlocks
        }
    }

    /**
     * 发送请求到指定服务 (本地或远程)
     */
    private sendRequestToService(serviceName: string, request: any): boolean {
        const serviceInfo = this.serviceRegistry.get(serviceName);
        if (!serviceInfo || !this.isServiceActive(serviceName)) {
            console.error(`Cannot send request: Service ${serviceName} is not active.`);
            return false;
        }

        const requestString = JSON.stringify(request) + '\n';

        try {
            if (serviceInfo.type === 'local') {
                const process = this.mcpProcesses.get(serviceName)!;
                return process.stdin.write(requestString);
            } else if (serviceInfo.type === 'remote') {
                // For remote services, we don't send raw requests. This is handled by handleToolCall.
                console.warn(`sendRequestToService called for remote service ${serviceName}, but this should be handled by specific methods like handleToolCall.`);
                return true;
            }
        } catch (e) {
            console.error(`Failed to write to service ${serviceName}: ${e}`);
            return false;
        }
        return false;
    }


    /**
     * 获取特定服务的MCP工具列表
     */
    private async fetchMcpTools(serviceName: string): Promise<void> {
        if (!this.isServiceActive(serviceName)) {
            // 如果进程/客户端不可用，设置空工具并标记为就绪
            this.mcpToolsMap.set(serviceName, []);
            this.serviceReadyMap.set(serviceName, true);
            console.log(`MCP service ${serviceName} marked ready with no tools (process unavailable)`);
            return;
        }

        const serviceInfo = this.serviceRegistry.get(serviceName);

        try {
            if (serviceInfo?.type === 'local') {
                // 创建tools/list请求
                const toolsListRequest = {
                    jsonrpc: '2.0',
                    id: `init_${serviceName}_${Date.now()}`,
                    method: 'tools/list',
                    params: {}
                };

                // 发送请求
                this.sendRequestToService(serviceName, toolsListRequest);
            } else if (serviceInfo?.type === 'remote') {
                const client = this.remoteServiceClients.get(serviceName);
                if (client) {
                    const tools = await client.getAllTools();
                    this.mcpToolsMap.set(serviceName, tools);
                    console.log(`MCP service ${serviceName} is ready with ${tools.length} tools`);
                }
            }
        } catch (error) {
            console.error(`Error fetching tools for ${serviceName}: ${error instanceof Error ? error.message : String(error)}`);
            this.mcpToolsMap.set(serviceName, []);
        } finally {
            // 标记服务为就绪状态
            this.serviceReadyMap.set(serviceName, true);
        }
    }

    /**
     * 处理来自特定服务的MCP响应数据
     */
    private handleMcpResponse(data: Buffer, serviceName: string): void {
        try {
            const responseText = data.toString().trim();
            if (!responseText) return;

            const response = JSON.parse(responseText);

            // 检查是否为工具调用响应
            if (response.id && this.toolResponseMapping.has(response.id)) {
                const bridgeRequestId = this.toolResponseMapping.get(response.id)!;
                const pendingRequest = this.pendingRequests.get(bridgeRequestId);

                if (pendingRequest) {
                    // 转发响应给客户端
                    pendingRequest.socket.write(JSON.stringify({
                        id: bridgeRequestId,
                        success: !response.error,
                        result: response.result,
                        error: response.error
                    }) + '\n');

                    // 清理
                    this.pendingRequests.delete(bridgeRequestId);
                    this.toolResponseMapping.delete(response.id);
                    this.toolCallServiceMap.delete(response.id);
                }
            } else if (response.id && typeof response.id === 'string' && response.id.startsWith(`init_${serviceName}_`)) {
                // 处理工具列表响应
                if (response.error) {
                    // 错误时设置空工具列表
                    this.mcpToolsMap.set(serviceName, []);
                    return;
                }

                // 从响应中提取工具
                let tools: any[] = [];
                if (response.result && response.result.tools && Array.isArray(response.result.tools)) {
                    tools = response.result.tools;
                } else if (response.result && Array.isArray(response.result)) {
                    tools = response.result;
                }

                // 存储该服务的工具
                this.mcpToolsMap.set(serviceName, tools);
                console.log(`MCP service ${serviceName} is ready with ${tools.length} tools`);
            }
        } catch (e) {
            console.error(`Error handling MCP response from ${serviceName}: ${e instanceof Error ? e.message : String(e)}`);

            // 不要崩溃服务器
            this.mcpToolsMap.set(serviceName, []);
        }
    }

    /**
     * 处理客户端MCP命令
     */
    private handleMcpCommand(command: McpCommand, socket: net.Socket): void {
        const { id, command: cmdType, params } = command;
        let response: McpResponse;

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
                        } else {
                            response = {
                                id,
                                success: false,
                                error: {
                                    code: -32601,
                                    message: `Service '${pingServiceName}' not registered`
                                }
                            };
                        }
                    } else {
                        // 普通bridge健康检查
                        const runningServices = [...this.mcpProcesses.keys(), ...this.remoteServiceClients.keys()];
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
                    const activeServices = [...this.mcpProcesses.keys(), ...this.remoteServiceClients.keys()];
                    const serviceStatus: Record<string, any> = {};

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
                        } else {
                            response = {
                                id,
                                success: true,
                                result: {
                                    tools: this.mcpToolsMap.get(serviceToList) || []
                                }
                            };
                        }
                    } else {
                        // 未指定服务，列出所有服务的工具
                        const allTools: Record<string, any> = {};
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
                            this.startMcpProcess(
                                spawnServiceName,
                                serviceInfo.command!,
                                serviceInfo.args!,
                                serviceInfo.env,
                                serviceInfo.cwd
                            );
                        } else if (serviceInfo.type === 'remote') {
                            this.connectToRemoteService(spawnServiceName, serviceInfo.endpoint!, serviceInfo.connectionType);
                        }
                    } else if (serviceCommand) {
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
                        this.startMcpProcess(spawnServiceName, serviceCommand, serviceArgs, serviceEnv, serviceCwd);
                    } else {
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
                    } else if (!this.isServiceActive(serviceToShutdown)) {
                        // 服务未运行
                        response = {
                            id,
                            success: false,
                            error: {
                                code: -32602,
                                message: `Service '${serviceToShutdown}' not active`
                            }
                        };
                    } else {
                        const serviceInfoToShutDown = this.serviceRegistry.get(serviceToShutdown)!;
                        // 终止进程或连接
                        if (serviceInfoToShutDown.type === 'local') {
                            this.mcpProcesses.get(serviceToShutdown)!.kill();
                            this.mcpProcesses.delete(serviceToShutdown);
                        } else if (serviceInfoToShutDown.type === 'remote') {
                            const client = this.remoteServiceClients.get(serviceToShutdown);
                            client?.close();
                            this.remoteServiceClients.delete(serviceToShutdown);
                        }


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
                        const serviceInfoToShutdown = this.serviceRegistry.get(serviceNameToUnregister)!;
                        if (serviceInfoToShutdown.type === 'local') {
                            this.mcpProcesses.get(serviceNameToUnregister)!.kill();
                            this.mcpProcesses.delete(serviceNameToUnregister);
                        } else if (serviceInfoToShutdown.type === 'remote') {
                            const client = this.remoteServiceClients.get(serviceNameToUnregister);
                            client?.close();
                            this.remoteServiceClients.delete(serviceNameToUnregister);
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
        } catch (error) {
            // 通用错误处理
            const errorResponse: McpResponse = {
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
    private async handleToolCall(command: McpCommand, socket: net.Socket): Promise<void> {
        const { id, params } = command;
        const { method, params: methodParams, name: requestedServiceName } = params || {};

        // 确定使用哪个服务
        const serviceName = requestedServiceName || this.mcpProcesses.keys().next().value || this.remoteServiceClients.keys().next().value;

        if (!serviceName) {
            console.error(`Cannot handle tool call: No service specified and no default available`);
            const response: McpResponse = {
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
            const response: McpResponse = {
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

            let toolCallResult: any;
            let toolCallError: any;

            if (serviceInfo.type === 'local') {
                // --- LOCAL SERVICE TOOLCALL (existing logic) ---
                const toolCallId = params.id || uuidv4();
                const toolCallRequest: ToolCallRequest = {
                    jsonrpc: '2.0',
                    id: toolCallId,
                    method: 'tools/call',
                    params: {
                        name: params.method,
                        arguments: params.params || {}
                    }
                };

                // Update pending request with toolCallId for response mapping
                this.pendingRequests.get(id)!.toolCallId = toolCallId;
                this.toolResponseMapping.set(toolCallId, id);
                this.toolCallServiceMap.set(toolCallId, serviceName);

                const writeResult = this.sendRequestToService(serviceName, toolCallRequest);
                if (!writeResult) {
                    throw new Error(`Failed to send request to ${serviceName}`);
                }
                // For local services, the response is handled asynchronously in handleMcpResponse, so we return here.
                return;

            } else if (serviceInfo.type === 'remote') {
                // --- REMOTE SERVICE TOOLCALL (new logic) ---
                const client = this.remoteServiceClients.get(serviceName);
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

            // --- SEND RESPONSE FOR REMOTE CALL ---
            // (Local call response is sent in handleMcpResponse)
            if (serviceInfo.type === 'remote') {
                const response: McpResponse = {
                    id,
                    success: !toolCallError,
                    result: toolCallResult,
                    error: toolCallError
                };
                socket.write(JSON.stringify(response) + '\n');
                this.pendingRequests.delete(id);
            }

        } catch (error) {
            console.error(`Error handling tool call for ${serviceName}: ${error instanceof Error ? error.message : String(error)}`);

            // 发送错误响应
            const response: McpResponse = {
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
                this.toolResponseMapping.delete(pendingRequest.toolCallId);
                this.toolCallServiceMap.delete(pendingRequest.toolCallId);
            }
            this.pendingRequests.delete(id);
        }
    }

    /**
     * 检查请求超时
     */
    private checkRequestTimeouts(): void {
        const now = Date.now();

        for (const [requestId, request] of this.pendingRequests.entries()) {
            if (now - request.timestamp > this.REQUEST_TIMEOUT) {
                console.log(`Request timeout: ${requestId}`);

                // 发送超时响应
                const response: McpResponse = {
                    id: requestId,
                    success: false,
                    error: {
                        code: -32603,
                        message: "Request timeout",
                        data: this.mcpErrors.get(this.toolCallServiceMap.get(requestId) || '') ?
                            { stderr: this.mcpErrors.get(this.toolCallServiceMap.get(requestId) || '') } :
                            undefined
                    }
                };

                request.socket.write(JSON.stringify(response) + '\n');

                // 清理
                this.pendingRequests.delete(requestId);
                if (request.toolCallId) {
                    this.toolResponseMapping.delete(request.toolCallId);
                    this.toolCallServiceMap.delete(request.toolCallId);
                }
            }
        }
    }

    /**
     * 启动TCP服务器
     */
    public start(): void {
        // 创建TCP服务器 - 默认不启动任何MCP进程
        this.server = net.createServer((socket: net.Socket) => {
            console.log(`New client connection: ${socket.remoteAddress}:${socket.remotePort}`);
            this.activeConnections.add(socket);

            // 添加socket超时以防止客户端挂起
            socket.setTimeout(30000); // 30秒超时
            socket.on('timeout', () => {
                console.log(`Socket timeout: ${socket.remoteAddress}:${socket.remotePort}`);
                socket.end();
                this.activeConnections.delete(socket);
            });

            // 处理来自客户端的数据
            socket.on('data', (data: Buffer) => {
                const message = data.toString().trim();

                try {
                    // 解析命令
                    const command = JSON.parse(message) as McpCommand;

                    // 确保命令有ID
                    if (!command.id) {
                        command.id = uuidv4();
                    }

                    // 处理命令
                    if (command.command) {
                        this.handleMcpCommand(command, socket);
                    } else {
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
                } catch (e) {
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
                            this.toolResponseMapping.delete(toolCallId);
                            this.toolCallServiceMap.delete(toolCallId);
                        }
                    }
                }
            });

            // 处理客户端错误
            socket.on('error', (err: Error) => {
                console.error(`Client error: ${err.message}`);
                this.activeConnections.delete(socket);
            });
        });

        // 启动TCP服务器
        this.server.listen(this.config.port, this.config.host, () => {
            console.log(`TCP bridge server running on ${this.config.host}:${this.config.port}`);
        });

        // 处理服务器错误
        this.server.on('error', (err: Error) => {
            console.error(`Server error: ${err.message}`);
        });

        // 处理进程信号
        process.on('SIGINT', () => this.shutdown());
        process.on('SIGTERM', () => this.shutdown());
    }

    /**
     * 关闭桥接器
     */
    public shutdown(): void {
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
        for (const [name, mcpProcess] of this.mcpProcesses.entries()) {
            console.log(`Terminating MCP process: ${name}`);
            mcpProcess.kill();
        }
        this.mcpProcesses.clear();

        // 关闭所有远程连接
        for (const [name, client] of this.remoteServiceClients.entries()) {
            console.log(`Closing remote connection: ${name}`);
            client.close();
        }
        this.remoteServiceClients.clear();

        console.log('Bridge shut down');
        process.exit(0);
    }

    /**
     * 从JSON-RPC请求中提取ID
     */
    private extractId(request: string): string | null {
        try {
            const json = JSON.parse(request);
            return json.id || null;
        } catch (e) {
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
export default McpBridge; 