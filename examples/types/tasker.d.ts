/**
 * Tasker type definitions for Assistance Package Tools
 */

import { TaskerResultData } from './results';

/**
 * Tasker task type definitions
 * These represent common Tasker event types that can be triggered
 */
export type TaskerTaskType = string;

/**
 * Tasker event arguments
 * Key-value pairs passed to the Tasker event
 */
export interface TaskerEventArgs {
    [key: string]: string;
}

/**
 * Tasker operations namespace
 */
export namespace Tasker {
    /**
     * Trigger a Tasker event
     * @param taskType - The type of task to trigger in Tasker
     * @param args - Optional arguments to pass to the Tasker event
     * @returns Promise resolving to TaskerResultData containing the trigger result
     * @throws Error if the Tasker event trigger fails
     * 
     * @example
     * ```typescript
     * // Trigger a simple Tasker event
     * await Tools.Tasker.trigger("MyTaskName");
     * 
     * // Trigger with arguments
     * await Tools.Tasker.trigger("ProcessData", {
     *     data: "example",
     *     priority: "high"
     * });
     * ```
     */
    function trigger(taskType: TaskerTaskType, args?: TaskerEventArgs): Promise<TaskerResultData>;
}

