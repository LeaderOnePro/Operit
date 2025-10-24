/**
 * Tool name type definitions for Assistance Package Tools
 * 
 * This file defines the available tool names and maps them to their result types.
 */

import {
    DirectoryListingData, FileContentData, FileOperationData, FileExistsData,
    FindFilesResultData, FileInfoData, FileConversionResultData, FileFormatConversionsResultData,
    HttpResponseData, VisitWebResultData,
    SleepResultData, SystemSettingData, AppOperationData, AppListData,
    DeviceInfoResultData, NotificationData, LocationData,
    UIPageResultData, UIActionResultData, CombinedOperationResultData,
    CalculationResultData, FFmpegResultData, ADBResultData, IntentResultData, TerminalCommandResultData,
    FilePartContentData, FileApplyResultData, WorkflowListResultData, WorkflowResultData, WorkflowDetailResultData,
    StringResultData
} from './results';

// ============================================================================
// Tool Name Types
// ============================================================================

/**
 * File tool names
 */
export type FileToolName = 'list_files' | 'read_file' | 'read_file_part' | 'read_file_full' | 'write_file' | 'delete_file' | 'file_exists' |
    'move_file' | 'copy_file' | 'make_directory' | 'find_files' | 'file_info' |
    'zip_files' | 'unzip_files' | 'open_file' | 'share_file' | 'download_file' |
    'apply_file';

/**
 * Network tool names
 */
export type NetToolName = 'http_request' | 'visit_web' | 'multipart_request' | 'manage_cookies';

/**
 * System tool names
 */
export type SystemToolName = 'sleep' | 'get_system_setting' | 'modify_system_setting' |
    'install_app' | 'uninstall_app' | 'list_installed_apps' | 'start_app' | 'stop_app' |
    'device_info' | 'execute_shell' | 'execute_intent' | 'execute_terminal' |
    'get_notifications' | 'get_device_location';

/**
 * UI tool names
 */
export type UiToolName = 'get_page_info' | 'click_element' | 'tap' | 'set_input_text' | 'press_key' |
    'swipe' | 'combined_operation';

/**
 * Calculator tool names
 */
export type CalculatorToolName = 'calculate';

/**
 * Connection tool names
 */
export type ConnectionToolName = 'establish_connection';

/**
 * Package tool names
 */
export type PackageToolName = 'use_package' | 'query_memory';

/**
 * FFmpeg tool names
 * Available FFmpeg-related tools in the system
 */
export type FFmpegToolName =
    /** Execute custom FFmpeg commands */
    | 'ffmpeg_execute'
    /** Get FFmpeg system information */
    | 'ffmpeg_info'
    /** Convert video files with simplified parameters */
    | 'ffmpeg_convert';

/**
 * Workflow tool names
 * Available workflow management tools in the system
 */
export type WorkflowToolName =
    /** Get all workflows */
    | 'get_all_workflows'
    /** Create a new workflow */
    | 'create_workflow'
    /** Get workflow details */
    | 'get_workflow'
    /** Update a workflow */
    | 'update_workflow'
    /** Delete a workflow */
    | 'delete_workflow'
    /** Trigger a workflow execution */
    | 'trigger_workflow';

/**
 * All tool names
 */
export type ToolName = FileToolName | NetToolName | SystemToolName | UiToolName |
    CalculatorToolName | ConnectionToolName | PackageToolName | FFmpegToolName | WorkflowToolName | string;

/**
 * Maps tool names to their result data types
 */
export interface ToolResultMap {
    // File operations
    'list_files': DirectoryListingData;
    'read_file': FileContentData;
    'read_file_part': FilePartContentData;
    'read_file_full': FileContentData;
    'write_file': FileOperationData;
    'delete_file': FileOperationData;
    'file_exists': FileExistsData;
    'move_file': FileOperationData;
    'copy_file': FileOperationData;
    'make_directory': FileOperationData;
    'find_files': FindFilesResultData;
    'file_info': FileInfoData;
    'zip_files': FileOperationData;
    'unzip_files': FileOperationData;
    'open_file': FileOperationData;
    'share_file': FileOperationData;
    'download_file': FileOperationData;
    'apply_file': FileApplyResultData;

    // Network operations
    'http_request': HttpResponseData;
    'visit_web': VisitWebResultData;
    'multipart_request': HttpResponseData;
    'manage_cookies': HttpResponseData;

    // System operations
    'sleep': SleepResultData;
    'get_system_setting': SystemSettingData;
    'modify_system_setting': SystemSettingData;
    'install_app': AppOperationData;
    'uninstall_app': AppOperationData;
    'list_installed_apps': AppListData;
    'start_app': AppOperationData;
    'stop_app': AppOperationData;
    'device_info': DeviceInfoResultData;
    'get_notifications': NotificationData;
    'get_device_location': LocationData;

    // UI operations
    'get_page_info': UIPageResultData;
    'click_element': UIActionResultData;
    'tap': UIActionResultData;
    'set_input_text': UIActionResultData;
    'press_key': UIActionResultData;
    'swipe': UIActionResultData;
    'combined_operation': CombinedOperationResultData;

    // Calculator operations
    'calculate': CalculationResultData;

    // Package operations
    'use_package': string;
    'query_memory': string;

    // FFmpeg operations
    'ffmpeg_execute': FFmpegResultData;
    'ffmpeg_info': FFmpegResultData;
    'ffmpeg_convert': FFmpegResultData;

    // ADB operations
    'execute_shell': ADBResultData;

    // Intent operations
    'execute_intent': IntentResultData;

    // Terminal operations
    'execute_terminal': TerminalCommandResultData;

    // Workflow operations
    'get_all_workflows': WorkflowListResultData;
    'create_workflow': WorkflowDetailResultData;
    'get_workflow': WorkflowDetailResultData;
    'update_workflow': WorkflowDetailResultData;
    'delete_workflow': StringResultData;
    'trigger_workflow': StringResultData;
} 