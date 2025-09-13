package com.ai.assistance.operit.terminal.service;

import com.ai.assistance.operit.terminal.service.ITerminalCallback;

/**
 * 终端服务接口
 * 这是与 TerminalService 交互的主要接口。
 */
interface ITerminalService {
    /**
     * 创建一个新的终端会话
     * @return 新会话的唯一ID
     */
    String createSession();

    /**
     * 切换到指定ID的会话
     * @param sessionId 要切换到的会话ID
     */
    void switchToSession(in String sessionId);

    /**
     * 关闭指定ID的会话
     * @param sessionId 要关闭的会话ID
     */
    void closeSession(in String sessionId);

    /**
     * 向当前会话发送一个命令
     * @param command 要执行的命令
     */
    void sendCommand(in String command);

    /**
     * 向当前会话发送中断信号 (Ctrl+C)
     */
    void sendInterruptSignal();

    /**
     * 注册一个回调以接收终端状态更新
     * @param callback 回调接口实现
     */
    void registerCallback(ITerminalCallback callback);

    /**
     * 取消注册一个回调
     * @param callback 要取消注册的回调接口
     */
    void unregisterCallback(ITerminalCallback callback);

    /**
     * 请求立即获取一次最新的终端状态
     */
    void requestStateUpdate();

    /**
     * 获取当前所有会话的列表
     * @return 会话ID列表
     */
    List<String> getSessionList();

    /**
     * 获取当前活动会话的ID
     * @return 当前会话ID，如果没有活动会话则返回null
     */
    String getCurrentSessionId();
} 