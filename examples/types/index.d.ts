/**
 * TypeScript definitions for Assistance Package Tools
 * 
 * This file provides type definitions for the JavaScript environment
 * available in package tools execution.
 */

// Import types that will be used in global declarations
import { ToolReturnType, NativeInterface as CoreNativeInterface } from './core';
import {
    _CalculationResultData, _SleepResultData, _SystemSettingData, _SystemSettingResult,
    _AppOperationData, _AppListData, _DeviceInfoResultData, _UIPageResultData,
    _UIActionResultData, _SimplifiedUINode, _FileOperationData, _DirectoryListingData,
    _FileContentData, _FileExistsData, _FindFilesResultData, _FileInfoData,
    _HttpResponseData, _VisitWebResultData, _CombinedOperationResultData,
    _FilePartContentData, _FileApplyResultData, _UiAutomationTaskResultData
} from './results';
import { Intent as AndroidIntent, IntentFlag as AndroidIntentFlag, IntentAction as AndroidIntentAction, IntentCategory as AndroidIntentCategory } from './android';
import { UINode as UINodeClass, UI as UINamespace } from './ui';
import { Android as AndroidClass } from './android';
import { CryptoJS } from './cryptojs';
import { Jimp } from './jimp';

// Export core interfaces and functions
export * from './core';

// Export all result types
export * from './results';

// Export tool type definitions
export * from './tool-types';

import { Files as FilesType } from './files';
import { Net as NetType } from './network';
import { System as SystemType } from './system';
import { UI as UIType } from './ui';
import { FFmpeg as FFmpegType } from './ffmpeg';
import { Query as QueryType } from './query';

export { Net } from './network';
export { System } from './system';
export { UI, UINode } from './ui';
export { FFmpegVideoCodec, FFmpegAudioCodec, FFmpegResolution, FFmpegBitrate } from './ffmpeg';
export { Query } from './query';

// Export Android utilities
export {
    AdbExecutor,
    IntentFlag,
    IntentAction,
    IntentCategory,
    Intent,
    PackageManager,
    ContentProvider,
    SystemManager,
    DeviceController,
    Android
} from './android';


// Global declarations (these will be available without imports)
declare global {
    // Make Android classes/constructs available globally
    const Intent: typeof AndroidIntent;
    const IntentFlag: typeof AndroidIntentFlag;
    const IntentAction: typeof AndroidIntentAction;
    const IntentCategory: typeof AndroidIntentCategory;
    const UINode: typeof UINodeClass;
    const Android: typeof AndroidClass;

    // Make classes available as types too
    type UINode = UINodeClass;
    type Android = AndroidClass;

    // Make OkHttp globally available
    const OkHttp: {
        newClient(): OkHttpClient;
        newBuilder(): OkHttpClientBuilder;
    };

    // Make result types available globally
    type CalculationResultData = _CalculationResultData;
    type SleepResultData = _SleepResultData;
    type SystemSettingData = _SystemSettingData;
    type SystemSettingResult = _SystemSettingResult;
    type AppOperationData = _AppOperationData;
    type AppListData = _AppListData;
    type DeviceInfoResultData = _DeviceInfoResultData;
    type UIPageResultData = _UIPageResultData;
    type UIActionResultData = _UIActionResultData;
    type SimplifiedUINode = _SimplifiedUINode;
    type FileOperationData = _FileOperationData;
    type DirectoryListingData = _DirectoryListingData;
    type FileContentData = _FileContentData;
    type FileExistsData = _FileExistsData;
    type FindFilesResultData = _FindFilesResultData;
    type FileInfoData = _FileInfoData;
    type HttpResponseData = _HttpResponseData;
    type VisitWebResultData = _VisitWebResultData;
    type CombinedOperationResultData = _CombinedOperationResultData;
    type FilePartContentData = _FilePartContentData;
    type FileApplyResultData = _FileApplyResultData;
    type UiAutomationTaskResultData = _UiAutomationTaskResultData;

    // Global interface definitions
    interface ToolParams {
        [key: string]: string | number | boolean | object;
    }

    interface ToolConfig {
        type?: string;
        name: string;
        params?: ToolParams;
    }

    // Tool call functions
    function toolCall<T extends string>(toolType: string, toolName: T, toolParams?: ToolParams): Promise<ToolReturnType<T>>;
    function toolCall<T extends string>(toolName: T, toolParams?: ToolParams): Promise<ToolReturnType<T>>;
    function toolCall<T extends string>(config: ToolConfig & { name: T }): Promise<ToolReturnType<T>>;
    function toolCall(toolName: string): Promise<any>;

    // Complete function
    function complete<T>(result: T): void;

    // Send intermediate result function
    function sendIntermediateResult<T>(result: T): void;

    // Utility objects
    const _: {
        isEmpty(value: any): boolean;
        isString(value: any): boolean;
        isNumber(value: any): boolean;
        isBoolean(value: any): boolean;
        isObject(value: any): boolean;
        isArray(value: any): boolean;
        forEach<T>(collection: T[] | object, iteratee: (value: any, key: any, collection: any) => void): any;
        map<T, R>(collection: T[] | object, iteratee: (value: any, key: any, collection: any) => R): R[];
    };

    const dataUtils: {
        parseJson(jsonString: string): any;
        stringifyJson(obj: any): string;
        formatDate(date?: Date | string): string;
    };

    // Tools namespace available globally
    const Tools: {
        Files: typeof FilesType;
        Net: typeof NetType;
        System: typeof SystemType;
        UI: typeof UIType;
        FFmpeg: typeof FFmpegType;
        Query: typeof QueryType;
        calc: (expression: string) => Promise<CalculationResultData>;
    };

    // CommonJS exports
    const exports: Record<string, any>;

    // NativeInterface
    const NativeInterface: typeof CoreNativeInterface;
}