/**
 * File operation type definitions for Assistance Package Tools
 */

import {
    DirectoryListingData, FileContentData, FileOperationData, FileExistsData,
    FindFilesResultData, FileInfoData,
    FileApplyResultData, GrepResultData
} from './results';
import { FFmpegVideoCodec, FFmpegAudioCodec, FFmpegResolution, FFmpegBitrate } from './ffmpeg';

/**
 * Execution environment for file operations
 */
export type FileEnvironment = "android" | "linux";

/**
 * File operations namespace
 */
export namespace Files {
    /**
     * List files in a directory
     * @param path - Path to directory
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function list(path: string, environment?: FileEnvironment): Promise<DirectoryListingData>;

    /**
     * Read file contents (always reads complete file)
     * @param path - Path to file
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function read(path: string, environment?: FileEnvironment): Promise<FileContentData>;

    /**
     * Write content to file
     * @param path - Path to file
     * @param content - Content to write
     * @param append - Whether to append to file
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function write(path: string, content: string, append?: boolean, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Write base64 encoded content to a binary file
     * @param path - Path to file
     * @param base64Content - Base64 encoded content to write
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function writeBinary(path: string, base64Content: string, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Delete a file or directory
     * @param path - Path to file or directory
     * @param recursive - Delete recursively
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function deleteFile(path: string, recursive?: boolean, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Check if file exists
     * @param path - Path to check
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function exists(path: string, environment?: FileEnvironment): Promise<FileExistsData>;

    /**
     * Move file from source to destination
     * @param source - Source path
     * @param destination - Destination path
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function move(source: string, destination: string, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Copy file from source to destination
     * @param source - Source path
     * @param destination - Destination path
     * @param recursive - Copy recursively
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function copy(source: string, destination: string, recursive?: boolean, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Create a directory
     * @param path - Directory path
     * @param create_parents - Create parent directories
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function mkdir(path: string, create_parents?: boolean, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Find files matching a pattern
     * @param path - Base directory
     * @param pattern - Search pattern
     * @param options - Search options
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function find(path: string, pattern: string, options?: Record<string, any>, environment?: FileEnvironment): Promise<FindFilesResultData>;

    /**
     * Search code content matching a regex pattern in files
     * @param path - Base directory to search
     * @param pattern - Regex pattern to search for
     * @param options - Search options
     * @param options.file_pattern - File filter pattern (e.g., "*.kt"), default "*"
     * @param options.case_insensitive - Ignore case in pattern matching, default false
     * @param options.context_lines - Number of context lines before/after each match, default 3
     * @param options.max_results - Maximum number of matches to return, default 100
     * @param options.environment - Execution environment ("android" or "linux"), default "android"
     */
    function grep(path: string, pattern: string, options?: {
        file_pattern?: string;
        case_insensitive?: boolean;
        context_lines?: number;
        max_results?: number;
        environment?: FileEnvironment;
    }): Promise<GrepResultData>;

    /**
     * Get information about a file
     * @param path - File path
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function info(path: string, environment?: FileEnvironment): Promise<FileInfoData>;

    /**
     * Apply AI-generated content to a file with intelligent merging
     * @param path - Path to file
     * @param content - Content to apply
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function apply(path: string, content: string, environment?: FileEnvironment): Promise<FileApplyResultData>;

    /**
     * Zip files/directories
     * @param source - Source path
     * @param destination - Destination path
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function zip(source: string, destination: string, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Unzip an archive
     * @param source - Source archive
     * @param destination - Target directory
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function unzip(source: string, destination: string, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Open a file with system handler
     * @param path - File path
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function open(path: string, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Share a file with other apps
     * @param path - File path
     * @param title - Share title
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function share(path: string, title?: string, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Download a file from URL
     * @param url - Source URL
     * @param destination - Destination path
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function download(url: string, destination: string, environment?: FileEnvironment): Promise<FileOperationData>;

} 