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
 * File operations namespace
 */
export namespace Files {
    /**
     * List files in a directory
     * @param path - Path to directory
     */
    function list(path: string): Promise<DirectoryListingData>;

    /**
     * Read file contents (always reads complete file)
     * @param path - Path to file
     */
    function read(path: string): Promise<FileContentData>;

    /**
     * Write content to file
     * @param path - Path to file
     * @param content - Content to write
     */
    function write(path: string, content: string, append?: boolean): Promise<FileOperationData>;

    /**
     * Write base64 encoded content to a binary file
     * @param path - Path to file
     * @param base64Content - Base64 encoded content to write
     */
    function writeBinary(path: string, base64Content: string): Promise<FileOperationData>;

    /**
     * Delete a file or directory
     * @param path - Path to file or directory
     */
    function deleteFile(path: string, recursive?: boolean): Promise<FileOperationData>;

    /**
     * Check if file exists
     * @param path - Path to check
     */
    function exists(path: string): Promise<FileExistsData>;

    /**
     * Move file from source to destination
     * @param source - Source path
     * @param destination - Destination path
     */
    function move(source: string, destination: string): Promise<FileOperationData>;

    /**
     * Copy file from source to destination
     * @param source - Source path
     * @param destination - Destination path
     */
    function copy(source: string, destination: string, recursive?: boolean): Promise<FileOperationData>;

    /**
     * Create a directory
     * @param path - Directory path
     */
    function mkdir(path: string, create_parents?: boolean): Promise<FileOperationData>;

    /**
     * Find files matching a pattern
     * @param path - Base directory
     * @param pattern - Search pattern
     */
    function find(path: string, pattern: string, options?: Record<string, any>): Promise<FindFilesResultData>;

    /**
     * Search code content matching a regex pattern in files
     * @param path - Base directory to search
     * @param pattern - Regex pattern to search for
     * @param options - Search options
     * @param options.file_pattern - File filter pattern (e.g., "*.kt"), default "*"
     * @param options.case_insensitive - Ignore case in pattern matching, default false
     * @param options.context_lines - Number of context lines before/after each match, default 3
     * @param options.max_results - Maximum number of matches to return, default 100
     */
    function grep(path: string, pattern: string, options?: {
        file_pattern?: string;
        case_insensitive?: boolean;
        context_lines?: number;
        max_results?: number;
    }): Promise<GrepResultData>;

    /**
     * Get information about a file
     * @param path - File path
     */
    function info(path: string): Promise<FileInfoData>;

    /**
     * Apply AI-generated content to a file with intelligent merging
     * @param path - Path to file
     * @param content - Content to apply
     */
    function apply(path: string, content: string): Promise<FileApplyResultData>;

    /**
     * Zip files/directories
     * @param source - Source path
     * @param destination - Destination path
     */
    function zip(source: string, destination: string): Promise<FileOperationData>;

    /**
     * Unzip an archive
     * @param source - Source archive
     * @param destination - Target directory
     */
    function unzip(source: string, destination: string): Promise<FileOperationData>;

    /**
     * Open a file with system handler
     * @param path - File path
     */
    function open(path: string): Promise<FileOperationData>;

    /**
     * Share a file with other apps
     * @param path - File path
     */
    function share(path: string, title?: string): Promise<FileOperationData>;

    /**
     * Download a file from URL
     * @param url - Source URL
     * @param destination - Destination path
     */
    function download(url: string, destination: string): Promise<FileOperationData>;

} 