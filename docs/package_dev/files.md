# API 文档: `files.d.ts`

本文档详细介绍了 `files.d.ts` 文件中定义的 API，该 API 提供了在脚本中进行文件和目录操作的全面功能。

## 概述

所有文件系统相关的功能都封装在全局的 `Tools.Files` 命名空间下。这个模块涵盖了从基本的读写、移动、删除，到更高级的压缩、下载和文件转换等操作。

---

## `Tools.Files` 命名空间详解

### 基本文件操作

-   `read(path: string): Promise<FileContentData>`: 读取一个文本文件的内容。对于大文件，可能只返回部分内容；若要读取完整内容，请使用 `readFull`。
-   `readFull(path: string): Promise<FileContentData>`: 强制读取并返回一个文本文件的全部内容。
-   `readPart(path: string, partIndex: number): Promise<FilePartContentData>`: 分块读取大文件，返回指定索引的部分。
-   `write(path: string, content: string): Promise<FileOperationData>`: 将文本内容写入一个文件。如果文件已存在，其内容将被覆盖。
-   `writeBinary(path: string, base64Content: string): Promise<FileOperationData>`: 将 Base64 编码的字符串解码为二进制数据并写入文件。
-   `list(path: string): Promise<DirectoryListingData>`: 列出指定目录下的所有文件和子目录。
-   `exists(path: string): Promise<FileExistsData>`: 检查指定路径的文件或目录是否存在。
-   `info(path: string): Promise<FileInfoData>`: 获取文件或目录的详细信息，如大小、修改日期、权限等。

### 文件和目录管理

-   `copy(source: string, destination: string): Promise<FileOperationData>`: 复制文件或目录。
-   `move(source: string, destination: string): Promise<FileOperationData>`: 移动或重命名文件或目录。
-   `deleteFile(path: string): Promise<FileOperationData>`: 删除一个文件或（递归删除）一个目录。
-   `mkdir(path: string): Promise<FileOperationData>`: 创建一个新目录。

### 高级文件操作

-   `find(path: string, pattern: string): Promise<FindFilesResultData>`: 在指定目录下根据模式（pattern）查找文件。
-   `zip(source: string, destination: string): Promise<FileOperationData>`: 将指定的文件或目录压缩成一个 zip 文件。
-   `unzip(source: string, destination: string): Promise<FileOperationData>`: 将一个 zip 压缩包解压到指定目录。
-   `download(url: string, destination: string): Promise<FileOperationData>`: 从给定的 URL 下载文件并保存到本地。
-   `apply(path: string, content: string): Promise<FileApplyResultData>`: **（AI特定功能）** 将 AI 生成的内容智能地应用（合并/修改）到现有文件中。

### 文件交互

-   `open(path: string): Promise<FileOperationData>`: 请求系统使用默认的应用打开指定文件。
-   `share(path: string): Promise<FileOperationData>`: 调用系统的分享菜单来分享指定文件。

### 文件格式转换

-   `convert(sourcePath: string, targetPath: string, options?: object): Promise<FileConversionResultData>`:
    转换文件格式。这是一个强大的功能，底层可能调用 FFmpeg 或其他库。
    -   **`options`** 对象可以包含 `quality`, `video_codec`, `audio_codec`, `resolution`, `bitrate` 等参数。
-   `getSupportedConversions(formatType?: string): Promise<FileFormatConversionsResultData>`:
    获取当前环境支持的文件格式转换类型。可以按 `image`, `audio`, `video`, `document` 等进行过滤。

**示例: 读写文件**
```typescript
async function readAndWriteExample() {
    const filePath = "/sdcard/Documents/my_note.txt";
    try {
        // 写入文件
        await Tools.Files.write(filePath, "这是第一行。\n这是第二行。");

        // 读取文件
        const fileContent = await Tools.Files.read(filePath);
        console.log(fileContent.content);

        // 删除文件
        await Tools.Files.deleteFile(filePath);

        complete({ success: true, message: "文件读写和删除操作完成。" });
    } catch (error) {
        complete({ success: false, message: `文件操作失败: ${error.message}` });
    }
}
```

**示例: 下载并解压文件**
```typescript
async function downloadAndUnzip() {
    const url = "https://example.com/archive.zip";
    const zipPath = "/sdcard/Download/archive.zip";
    const extractPath = "/sdcard/Download/my_files/";

    try {
        await Tools.Files.download(url, zipPath);
        await Tools.Files.unzip(zipPath, extractPath);
        complete({ success: true, message: "文件已下载并解压。" });
    } catch (error) {
        complete({ success: false, message: `处理失败: ${error.message}` });
    }
}
``` 