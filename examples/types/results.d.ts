/**
 * Result interface definitions for Assistance Package Tools
 * 
 * This file provides type definitions for all result data structures
 * returned by the various tools.
 */

import { BaseResult } from './core';

// ============================================================================
// Calculation and Date Result Types
// ============================================================================

/**
 * Calculation result data
 */
export interface CalculationResultData {
    expression: string;
    result: number;
    formattedResult: string;
    variables: Record<string, number>;
    toString(): string;
}

/**
 * Date result data
 */
export interface DateResultData {
    /* Missing in original file but needed for DateResult interface */
    date: Date;
    formattedDate: string;
    timestamp: number;
    toString(): string;
}

// ============================================================================
// Connection Result Types
// ============================================================================

/**
 * Connection result data
 */
export interface ConnectionResultData {
    connectionId: string;
    isActive: boolean;
    timestamp: number;
    toString(): string;
}

// ============================================================================
// File Operation Types
// ============================================================================

/**
 * File entry in directory listing
 */
export interface FileEntry {
    name: string;
    isDirectory: boolean;
    size: number;
    permissions: string;
    lastModified: string;
    toString(): string;
}

export interface FileExistsData {
    path: string;
    exists: boolean;
    isDirectory?: boolean;
    size?: number;
}

/**
 * Detailed file information data
 */
export interface FileInfoData {
    path: string;
    exists: boolean;
    fileType: string;  // "file", "directory", or "other"
    size: number;
    permissions: string;
    owner: string;
    group: string;
    lastModified: string;
    rawStatOutput: string;
}

/**
 * Directory listing data
 */
export interface DirectoryListingData {
    path: string;
    entries: FileEntry[];
    toString(): string;
}

/**
 * File content data
 */
export interface FileContentData {
    path: string;
    content: string;
    size: number;
    toString(): string;
}

/**
 * File part content data
 */
export interface FilePartContentData {
    path: string;
    content: string;
    partIndex: number;
    totalParts: number;
    startLine: number;
    endLine: number;
    totalLines: number;
    toString(): string;
}

/**
 * File operation data
 */
export interface FileOperationData {
    operation: string;
    path: string;
    successful: boolean;
    details: string;
    toString(): string;
}

/**
 * File apply result data
 */
export interface FileApplyResultData {
    operation: FileOperationData;
    aiDiffInstructions: string;
    toString(): string;
}

/**
 * Find files result data
 */
export interface FindFilesResultData {
    path: string;
    pattern: string;
    files: string[];
    toString(): string;
}

/**
 * Line match in grep result
 */
export interface GrepLineMatch {
    lineNumber: number;
    lineContent: string;
    matchContext?: string;
}

/**
 * File match in grep result
 */
export interface GrepFileMatch {
    filePath: string;
    lineMatches: GrepLineMatch[];
}

/**
 * Grep search result data
 */
export interface GrepResultData {
    searchPath: string;
    pattern: string;
    filePattern?: string;
    matches: GrepFileMatch[];
    totalMatches: number;
    filesSearched: number;
    toString(): string;
}

/**
 * File conversion result data
 */
export interface FileConversionResultData {
    sourcePath: string;
    targetPath: string;
    sourceFormat: string;
    targetFormat: string;
    conversionType: string;  // "document", "image", "audio", "video", "archive", "extract"
    quality?: string;
    fileSize: number;
    duration: number;
    metadata: Record<string, string>; // Contains extra info like error details, password status for encrypted archives
    toString(): string;
}

/**
 * File format conversion support data
 */
export interface FileFormatConversionsResultData {
    formatType?: string;
    conversions: Record<string, string[]>;
    fileTypes: Record<string, string[]>;
    toString(): string;
}

// ============================================================================
// HTTP and Network Types
// ============================================================================

/**
 * HTTP response data
 */
export interface HttpResponseData {
    url: string;
    statusCode: number;
    statusMessage: string;
    headers: Record<string, string>;
    contentType: string;
    content: string;
    size: number;
    toString(): string;
}

/**
 * Web page link
 */
export interface Link {
    text: string;
    url: string;
    toString(): string;
}


/**
 * Web page visit result data
 */
export interface VisitWebResultData {
    url: string;
    title: string;
    content: string;
    metadata?: Record<string, string>;
    toString(): string;
}

// ============================================================================
// Device Information Types
// ============================================================================

/**
 * Device information result data
 */
export interface DeviceInfoResultData {
    deviceId: string;
    model: string;
    manufacturer: string;
    androidVersion: string;
    sdkVersion: number;
    screenResolution: string;
    screenDensity: number;
    totalMemory: string;
    availableMemory: string;
    totalStorage: string;
    availableStorage: string;
    batteryLevel: number;
    batteryCharging: boolean;
    cpuInfo: string;
    networkType: string;
    additionalInfo: Record<string, string>;
    toString(): string;
}

// ============================================================================
// System Settings and App Types
// ============================================================================

/**
 * Sleep result data
 */
export interface SleepResultData {
    sleptMs: number;
    requestedMs: number;
    toString(): string;
}

/**
 * System setting data
 */
export interface SystemSettingData {
    namespace: string;
    setting: string;
    value: string;
    toString(): string;
}

/**
 * App operation data
 */
export interface AppOperationData {
    operationType: string;
    packageName: string;
    success: boolean;
    details: string;
    toString(): string;
}

/**
 * App list data
 */
export interface AppListData {
    includesSystemApps: boolean;
    packages: string[];
    toString(): string;
}

/**
 * Notification data structure
 */
export interface NotificationData {
    /** List of notification objects */
    notifications: Array<{
        /** The package name of the application that posted the notification */
        packageName: string;
        /** The text content of the notification */
        text: string;
        /** Timestamp when the notification was captured */
        timestamp: number;
    }>;
    /** Timestamp when the notifications were retrieved */
    timestamp: number;
    /** Returns a formatted string representation of the notifications */
    toString(): string;
}

/**
 * Location data structure
 */
export interface LocationData {
    /** Latitude coordinate in decimal degrees */
    latitude: number;
    /** Longitude coordinate in decimal degrees */
    longitude: number;
    /** Accuracy of the location in meters */
    accuracy: number;
    /** Location provider (e.g., "gps", "network", etc.) */
    provider: string;
    /** Timestamp when the location was retrieved */
    timestamp: number;
    /** Raw location data from the system */
    rawData: string;
    /** Street address determined from coordinates */
    address?: string;
    /** City name determined from coordinates */
    city?: string;
    /** Province/state name determined from coordinates */
    province?: string;
    /** Country name determined from coordinates */
    country?: string;
    /** Returns a formatted string representation of the location */
    toString(): string;
}

// ============================================================================
// UI Types
// ============================================================================

/**
 * UI node structure for hierarchical display
 */
export interface SimplifiedUINode {
    className?: string;
    text?: string;
    contentDesc?: string;
    resourceId?: string;
    bounds?: string;
    isClickable: boolean;
    children: SimplifiedUINode[];
    toString(): string;
    toTreeString(indent?: string): string;
    shouldKeepNode?(): boolean;
}

/**
 * UI page result data
 */
export interface UIPageResultData {
    packageName: string;
    activityName: string;
    uiElements: SimplifiedUINode;
    toString(): string;
}

/**
 * UI action result data
 */
export interface UIActionResultData {
    actionType: string;
    actionDescription: string;
    coordinates?: [number, number];
    elementId?: string;
    toString(): string;
}

/**
 * Combined operation result data
 */
export interface CombinedOperationResultData {
    operationSummary: string;
    waitTime: number;
    pageInfo: UIPageResultData;
    toString(): string;
}



/**
 * ADB command execution result data
 */
export interface ADBResultData {
    /** The ADB command that was executed */
    command: string;

    /** The output from the ADB command execution */
    output: string;

    /** Exit code from the ADB command (0 typically means success) */
    exitCode: number;

    /** Returns a formatted string representation of the ADB execution result */
    toString(): string;
}

/**
 * Intent execution result data
 */
export interface IntentResultData {
    action: string;
    uri: string;
    package_name: string;
    component: string;
    flags: number;
    extras_count: number;
    result: string;
    type: 'activity' | 'broadcast' | 'service';
    toString(): string;
}

/**
 * Terminal command execution result data
 */
export interface TerminalCommandResultData {
    /** The command that was executed */
    command: string;

    /** The output from the command execution */
    output: string;

    /** Exit code from the command (0 typically means success) */
    exitCode: number;

    /** ID of the terminal session used for execution */
    sessionId: string;

    /** Returns a formatted string representation of the terminal execution result */
    toString(): string;
}

/**
 * Terminal session creation result data
 */
export interface TerminalSessionCreationResultData {
    /** ID of the created or retrieved session */
    sessionId: string;
    /** Name of the session */
    sessionName: string;
    /** Whether a new session was created */
    isNewSession: boolean;
}

/**
 * Terminal session close result data
 */
export interface TerminalSessionCloseResultData {
    /** ID of the closed session */
    sessionId: string;
    /** Whether the session was closed successfully */
    success: boolean;
    /** A message describing the result */
    message: string;
}

// ============================================================================
// FFmpeg Types
// ============================================================================

import { FFmpegVideoCodec, FFmpegAudioCodec } from './ffmpeg';

/**
 * FFmpeg stream information
 * Represents detailed information about a video or audio stream in a media file
 */
export interface FFmpegStreamInfo {
    /** Stream index in the media file (0-based) */
    index: number;

    /** Stream type: "video" or "audio" */
    type: 'video' | 'audio';

    /** Codec name used for this stream */
    codec: FFmpegVideoCodec | FFmpegAudioCodec;

    /** Frame rate for video streams (e.g., "30/1", "29.97") */
    frameRate?: `${number}/${number}` | `${number}`;

    /** Sample rate for audio streams in Hz (e.g., "44100") */
    sampleRate?: `${number}`;

    /** Number of audio channels (e.g., 2 for stereo) */
    channels?: 1 | 2 | 4 | 6 | 8;

    /** Returns a formatted string representation of the stream info */
    toString(): string;
}

/**
 * FFmpeg result data
 * Contains comprehensive information about the FFmpeg operation execution
 */
export interface FFmpegResultData {
    /** The complete FFmpeg command that was executed */
    command: string;

    /** FFmpeg return code (0 indicates success) */
    returnCode: number;

    /** Complete output from the FFmpeg command execution */
    output: string;

    /** Execution duration in milliseconds */
    duration: number;

    /** Array of video stream information */
    videoStreams: FFmpegStreamInfo[];

    /** Array of audio stream information */
    audioStreams: FFmpegStreamInfo[];

    /** Returns a formatted string representation of the result */
    toString(): string;
}

// ============================================================================
// Result Type Wrappers
// ============================================================================

export interface CalculationResult extends BaseResult {
    data: CalculationResultData;
}

export interface DateResult extends BaseResult {
    data: DateResultData;
}

export interface ConnectionResult extends BaseResult {
    data: ConnectionResultData;
}

export interface DirectoryListingResult extends BaseResult {
    data: DirectoryListingData;
}

export interface FileContentResult extends BaseResult {
    data: FileContentData;
}

export interface FilePartContentResult extends BaseResult {
    data: FilePartContentData;
}

export interface FileOperationResult extends BaseResult {
    data: FileOperationData;
}

export interface FileApplyResult extends BaseResult {
    data: FileApplyResultData;
}

export interface HttpResponseResult extends BaseResult {
    data: HttpResponseData;
}

export interface VisitWebResult extends BaseResult {
    data: VisitWebResultData;
}

export interface SystemSettingResult extends BaseResult {
    data: SystemSettingData;
}

export interface AppOperationResult extends BaseResult {
    data: AppOperationData;
}

export interface AppListResult extends BaseResult {
    data: AppListData;
}

export interface UIPageResult extends BaseResult {
    data: UIPageResultData;
}

export interface UIActionResult extends BaseResult {
    data: UIActionResultData;
}



export interface FileConversionResult extends BaseResult {
    data: FileConversionResultData;
}

export interface FileFormatConversionsResult extends BaseResult {
    data: FileFormatConversionsResultData;
}

export interface ADBResult extends BaseResult {
    data: ADBResultData;
}

export interface IntentResult extends BaseResult {
    data: IntentResultData;
}

export interface TerminalCommandResult extends BaseResult {
    data: TerminalCommandResultData;
}

export interface TerminalSessionCreationResult extends BaseResult {
    data: TerminalSessionCreationResultData;
}

export interface TerminalSessionCloseResult extends BaseResult {
    data: TerminalSessionCloseResultData;
}

export interface DeviceInfoResult extends BaseResult {
    data: DeviceInfoResultData;
}

export interface CombinedOperationResult extends BaseResult {
    data: CombinedOperationResultData;
} 