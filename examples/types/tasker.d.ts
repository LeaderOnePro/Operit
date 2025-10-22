/**
 * Tasker tool type definitions for Assistance Package Tools
 */

export namespace Tasker {
    /**
     * Parameters for triggering a Tasker event.
     */
    export interface TriggerTaskerEventParams {
        /** 事件类型标识 */
        task_type: string;
        /** 可选参数1 */
        arg1?: string;
        /** 可选参数2 */
        arg2?: string;
        /** 可选参数3 */
        arg3?: string;
        /** 可选参数4 */
        arg4?: string;
        /** 可选参数5 */
        arg5?: string;
        /** 以JSON形式传递任意参数，请传入JSON字符串 */
        args_json?: string;
    }

    /**
     * Trigger a Tasker event.
     * Returns a short message string from native layer.
     */
    export function triggerEvent(params: TriggerTaskerEventParams): Promise<string>;
}