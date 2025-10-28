import { MCPClient } from 'mcp-client';
import * as path from 'path';
import * as os from 'os';
import { McpServiceInfo } from './index'; // Assuming McpServiceInfo is exported from index.ts

let client: MCPClient | null = null;
let serviceName: string;
let serviceInfo: McpServiceInfo;

/**
 * 展开路径中的 ~ 符号为用户主目录
 */
function expandPath(filePath: string): string {
    if (!filePath) return filePath;
    if (filePath.startsWith('~/') || filePath === '~') {
        return path.join(os.homedir(), filePath.slice(1));
    }
    return filePath;
}

const handlers = {
    async init(params: { serviceName: string, serviceInfo: McpServiceInfo }) {
        serviceName = params.serviceName;
        serviceInfo = params.serviceInfo;

        client = new MCPClient({
            name: `helper-for-${serviceName}`,
            version: '1.0.0',
        });

        try {
            if (serviceInfo.type === 'local') {
                let workingDir = serviceInfo.cwd || path.join(os.homedir(), 'mcp_plugins', serviceName);
                workingDir = expandPath(workingDir);

                let actualCommand = expandPath(serviceInfo.command!);
                let actualArgs = serviceInfo.args || [];
                if (actualCommand === 'npx') {
                    actualCommand = 'pnpm';
                    const filteredArgs = actualArgs.filter((arg: string) => arg !== '-y' && arg !== '--yes');
                    actualArgs = ['dlx', ...filteredArgs];
                }

                const mergedEnv = {
                    ...process.env,
                    ...serviceInfo.env,
                    ...(serviceInfo.env?.npm_config_cache ? {} : { npm_config_cache: path.join(workingDir, '.npm-cache') }),
                    ...(serviceInfo.env?.npm_config_prefer_offline ? {} : { npm_config_prefer_offline: 'true' }),
                    ...(serviceInfo.env?.UV_LINK_MODE ? {} : { UV_LINK_MODE: 'copy' }),
                    ...(os.platform() === 'linux' ? { NODE_OPTIONS: '--openssl-legacy-provider' } : {}),
                };

                await client.connect({
                    type: 'stdio',
                    command: actualCommand,
                    args: actualArgs,
                    cwd: workingDir,
                    env: mergedEnv,
                });

            } else if (serviceInfo.type === 'remote') {
                await client.connect({
                    type: serviceInfo.connectionType || 'httpStream',
                    url: serviceInfo.endpoint!,
                });
            }

            console.log(`[${serviceName}] Helper connected successfully.`);

            // Fetch tools and notify parent
            const tools = await client.getAllTools();
            process.send!({
                event: 'ready',
                params: { serviceName, tools }
            });

        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            console.error(`[${serviceName}] Helper connection failed: ${errorMessage}`);
            process.send!({
                event: 'closed',
                params: { serviceName, error: errorMessage }
            });
            // Exit after sending error to allow parent to restart
            process.exit(1);
        }
    },

    async toolcall(params: { id: string, name: string, args: any }) {
        if (!client) {
            process.send!({
                event: 'tool_result',
                id: params.id,
                result: { success: false, error: { message: 'Client not initialized' } }
            });
            return;
        }
        try {
            const result = await client.callTool({
                name: params.name,
                arguments: params.args || {},
            });

            const toolCallResult = { content: result.content };
            const toolCallError = result.isError ? { code: -32000, message: result.content[0]?.text || "Remote tool error" } : undefined;

            process.send!({
                event: 'tool_result',
                id: params.id,
                result: {
                    success: !toolCallError,
                    result: toolCallResult,
                    error: toolCallError
                }
            });

        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            process.send!({
                event: 'tool_result',
                id: params.id,
                result: {
                    success: false,
                    error: { message: `Tool call failed: ${errorMessage}` }
                }
            });
        }
    },

    async shutdown() {
        if (client) {
            await client.close();
        }
        process.exit(0);
    }
};

process.on('message', async (message: { command: string, id?: string, params: any }) => {
    const handler = handlers[message.command as keyof typeof handlers];
    if (handler) {
        if (message.command === 'toolcall') {
            await handler({ id: message.id!, ...message.params });
        } else {
            await (handler as (p: any) => Promise<void>)(message.params);
        }
    } else {
        console.error(`[${serviceName}] Helper received unknown command: ${message.command}`);
    }
});

// Handle process exit
process.on('disconnect', () => {
    console.log(`[${serviceName}] Parent process disconnected. Shutting down helper.`);
    handlers.shutdown();
});

// Prevent crashing the helper process on unhandled exceptions
process.on('uncaughtException', (err) => {
    console.error(`[${serviceName}] Uncaught exception in helper: ${err.stack}`);
    process.send!({
        event: 'closed',
        params: { serviceName, error: `Uncaught exception: ${err.message}` }
    });
    process.exit(1);
});

process.on('unhandledRejection', (reason, promise) => {
    console.error(`[${serviceName}] Unhandled rejection in helper:`, reason);
    process.send!({
        event: 'closed',
        params: { serviceName, error: `Unhandled rejection: ${reason}` }
    });
    process.exit(1);
});

console.log('Spawn helper process started.'); 