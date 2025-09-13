package com.ai.assistance.operit.terminal.service;

import com.ai.assistance.operit.terminal.service.TerminalStateParcelable;

/**
 * 终端回调接口
 * 这是一个单向（oneway）接口，用于从服务接收状态更新。客户端需要实现此接口。
 */
interface ITerminalCallback {
    /**
     * 当终端状态发生变化时由服务调用此方法
     * @param state 更新的终端状态
     */
    oneway void onStateUpdated(in TerminalStateParcelable state);
} 