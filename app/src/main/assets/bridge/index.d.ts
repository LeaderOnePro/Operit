/**
 * MCP TCP Bridge
 *
 * Creates a bridge that connects STDIO-based MCP servers to TCP clients
 */
interface BridgeConfig {
    port: number;
    host: string;
    mcpCommand: string;
    mcpArgs: string[];
    registryPath?: string;
    env?: Record<string, string>;
}
/**
 * MCP Bridge class
 */
declare class McpBridge {
    private config;
    private server;
    private serviceClients;
    private mcpToolsMap;
    private serviceReadyMap;
    private serviceRegistry;
    private activeConnections;
    private pendingRequests;
    private readonly REQUEST_TIMEOUT;
    private mcpErrors;
    private restartAttempts;
    private readonly MAX_RESTART_ATTEMPTS;
    private readonly RESTART_DELAY_MS;
    constructor(config?: Partial<BridgeConfig>);
    /**
     * 注册新的MCP服务
     */
    private registerService;
    /**
     * 注销MCP服务
     */
    private unregisterService;
    /**
     * 获取已注册MCP服务列表
     */
    private getServiceList;
    /**
     * 检查服务是否激活 (运行中或已连接)
     */
    private isServiceActive;
    /**
     * 连接到远程MCP服务 (HTTP/SSE)
     */
    private connectToRemoteService;
    /**
     * 处理服务连接关闭和重连 (本地和远程服务统一处理)
     */
    private handleServiceClosure;
    /**
     * 启动本地MCP服务 (使用官方 MCPClient 的 stdio 连接)
     */
    /**
     * 展开路径中的 ~ 符号为用户主目录
     */
    private expandPath;
    private startLocalService;
    /**
     * 获取特定服务的MCP工具列表 (统一处理本地和远程服务)
     */
    private fetchMcpTools;
    /**
     * 处理客户端MCP命令
     */
    private handleMcpCommand;
    /**
     * 处理工具调用请求
     */
    private handleToolCall;
    /**
     * 检查请求超时
     */
    private checkRequestTimeouts;
    /**
     * 启动TCP服务器
     */
    start(): void;
    /**
     * 关闭桥接器
     */
    shutdown(): void;
    /**
     * 从JSON-RPC请求中提取ID
     */
    private extractId;
}
export default McpBridge;
