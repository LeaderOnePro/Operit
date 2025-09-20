/* METADATA
{
  name: code_runner
  description: 提供多语言代码执行能力，支持JavaScript、Python、Ruby、Go和Rust脚本的运行。可直接执行代码字符串或运行外部文件，适用于快速测试、自动化脚本和教学演示。
  enabledByDefault: true
  
  // Multiple tools in this package
  tools: [
    {
      name: run_javascript_es5
      description: 运行自定义 JavaScript 脚本
      // This tool takes parameters
      parameters: [
        {
          name: script
          description: 要执行的 JavaScript 脚本内容
          type: string
          required: true
        }
      ]
    },
    {
      name: run_javascript_file
      description: 运行 JavaScript 文件
      parameters: [
        {
          name: file_path
          description: JavaScript 文件路径
          type: string
          required: true
        }
      ]
    },
    {
      name: run_python
      description: 运行自定义 Python 脚本
      parameters: [
        {
          name: script
          description: 要执行的 Python 脚本内容
          type: string
          required: true
        }
      ]
    },
    {
      name: run_python_file
      description: 运行 Python 文件
      parameters: [
        {
          name: file_path
          description: Python 文件路径
          type: string
          required: true
        }
      ]
    },
    {
      name: run_ruby
      description: 运行自定义 Ruby 脚本
      parameters: [
        {
          name: script
          description: 要执行的 Ruby 脚本内容
          type: string
          required: true
        }
      ]
    },
    {
      name: run_ruby_file
      description: 运行 Ruby 文件
      parameters: [
        {
          name: file_path
          description: Ruby 文件路径
          type: string
          required: true
        }
      ]
    },
    {
      name: run_go
      description: 运行自定义 Go 代码
      parameters: [
        {
          name: script
          description: 要执行的 Go 代码内容
          type: string
          required: true
        }
      ]
    },
    {
      name: run_go_file
      description: 运行 Go 文件
      parameters: [
        {
          name: file_path
          description: Go 文件路径
          type: string
          required: true
        }
      ]
    },
    {
      name: run_rust
      description: 运行自定义 Rust 代码
      parameters: [
        {
          name: script
          description: 要执行的 Rust 代码内容
          type: string
          required: true
        }
      ]
    },
    {
      name: run_rust_file
      description: 运行 Rust 文件
      parameters: [
        {
          name: file_path
          description: Rust 文件路径
          type: string
          required: true
        }
      ]
    }
  ]
  
  // Tool category
  category: SYSTEM_OPERATION
}
*/
const codeRunner = (function () {
    // Helper function to execute a terminal command using the new session-based API
    async function executeTerminalCommand(command, timeoutMs) {
        // Use a consistent session name to allow for session reuse
        const session = await Tools.System.terminal.create("code_runner_session");
        return await Tools.System.terminal.exec(session.sessionId, command, timeoutMs);
    }
    async function main() {
        const results = {
            javascript: await testJavaScript(),
            python: await testPython(),
            ruby: await testRuby(),
            go: await testGo(),
            rust: await testRust()
        };
        // Format results for display
        let summary = "代码执行器功能测试结果：\n";
        for (const [lang, result] of Object.entries(results)) {
            summary += `${lang}: ${result.success ? '✅ 成功' : '❌ 失败'} - ${result.message}\n`;
        }
        return summary;
    }
    // 测试JavaScript执行功能
    async function testJavaScript() {
        try {
            // 测试简单的JS代码
            const script = "const testVar = 42; return 'JavaScript运行正常，测试值: ' + testVar;";
            const result = await run_javascript_es5({ script });
            const expected = 'JavaScript运行正常，测试值: 42';
            if (result !== expected) {
                return { success: false, message: `JavaScript执行器测试失败: 期望 "${expected}", 实际 "${result}"` };
            }
            return { success: true, message: "JavaScript执行器测试成功" };
        }
        catch (error) {
            return { success: false, message: `JavaScript执行器测试失败: ${error.message}` };
        }
    }
    // 测试Python执行功能  
    async function testPython() {
        try {
            // 检查Python是否可用
            const pythonCheckResult = await executeTerminalCommand("python3 --version", 10000);
            if (pythonCheckResult.exitCode !== 0) {
                return { success: false, message: "Python不可用，请确保已安装Python" };
            }
            // 测试简单的Python代码
            const script = "print('Python运行正常')";
            await run_python({ script });
            return { success: true, message: "Python执行器测试成功" };
        }
        catch (error) {
            return { success: false, message: `Python执行器测试失败: ${error.message}` };
        }
    }
    // 测试Ruby执行功能
    async function testRuby() {
        try {
            // 检查Ruby是否可用
            const rubyCheckResult = await executeTerminalCommand("ruby --version", 10000);
            if (rubyCheckResult.exitCode !== 0) {
                return { success: false, message: "Ruby不可用，请确保已安装Ruby" };
            }
            // 测试简单的Ruby代码
            const script = "puts 'Ruby运行正常'";
            await run_ruby({ script });
            return { success: true, message: "Ruby执行器测试成功" };
        }
        catch (error) {
            return { success: false, message: `Ruby执行器测试失败: ${error.message}` };
        }
    }
    // 测试Go执行功能
    async function testGo() {
        try {
            // 检查Go是否可用
            const goCheckResult = await executeTerminalCommand("go version", 10000);
            if (goCheckResult.exitCode !== 0) {
                return { success: false, message: "Go不可用，请确保已安装Go" };
            }
            // 测试简单的Go代码
            const script = `
package main

import "fmt"

func main() {
  fmt.Println("Go运行正常")
}`;
            await run_go({ script });
            return { success: true, message: "Go执行器测试成功" };
        }
        catch (error) {
            return { success: false, message: `Go执行器测试失败: ${error.message}` };
        }
    }
    // 测试Rust执行功能
    async function testRust() {
        try {
            // 检查Rust是否可用
            const rustCheckResult = await executeTerminalCommand("rustc --version", 10000);
            if (rustCheckResult.exitCode !== 0) {
                return { success: false, message: "Rust不可用，请确保已安装Rust" };
            }
            // 测试简单的Rust代码
            const script = `
fn main() {
  println!("Rust运行正常");
}`;
            await run_rust({ script });
            return { success: true, message: "Rust执行器测试成功" };
        }
        catch (error) {
            return { success: false, message: `Rust执行器测试失败: ${error.message}` };
        }
    }
    async function run_javascript_es5(params) {
        const script = params.script;
        if (!script || script.trim() === "") {
            throw new Error("请提供要执行的脚本内容");
        }
        // Wrap in a function to allow return statements
        return eval(`(function(){${script}})()`);
    }
    async function run_javascript_file(params) {
        const filePath = params.file_path;
        if (!filePath || filePath.trim() === "") {
            throw new Error("请提供要执行的 JavaScript 文件路径");
        }
        const fileResult = await Tools.Files.read(filePath);
        if (!fileResult || !fileResult.content) {
            throw new Error(`无法读取文件: ${filePath}`);
        }
        // Wrap in a function to allow return statements
        return eval(`(function(){${fileResult.content}})()`);
    }
    async function run_python(params) {
        const script = params.script;
        if (!script || script.trim() === "") {
            throw new Error("请提供要执行的 Python 脚本内容");
        }
        const tempFilePath = "/sdcard/Download/Operit/temp_script.py";
        try {
            await Tools.Files.write(tempFilePath, script);
            const result = await executeTerminalCommand(`python3 ${tempFilePath}`, 30000);
            if (result.exitCode === 0) {
                return result.output.trim();
            }
            else {
                throw new Error(`Python 脚本执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
            }
        }
        finally {
            await Tools.Files.deleteFile(tempFilePath).catch(err => console.error(`删除临时文件失败: ${err.message}`));
        }
    }
    async function run_python_file(params) {
        const filePath = params.file_path;
        if (!filePath || filePath.trim() === "") {
            throw new Error("请提供要执行的 Python 文件路径");
        }
        const fileExists = await Tools.Files.exists(filePath);
        if (!fileExists || !fileExists.exists) {
            throw new Error(`Python 文件不存在: ${filePath}`);
        }
        const result = await executeTerminalCommand(`python3 ${filePath}`, 30000);
        if (result.exitCode === 0) {
            return result.output.trim();
        }
        else {
            throw new Error(`Python 文件执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
        }
    }
    async function run_ruby(params) {
        const script = params.script;
        if (!script || script.trim() === "") {
            throw new Error("请提供要执行的 Ruby 脚本内容");
        }
        const tempFilePath = "/sdcard/Download/Operit/temp_script.rb";
        try {
            await Tools.Files.write(tempFilePath, script);
            const result = await executeTerminalCommand(`ruby ${tempFilePath}`, 30000);
            if (result.exitCode === 0) {
                return result.output.trim();
            }
            else {
                throw new Error(`Ruby 脚本执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
            }
        }
        finally {
            await Tools.Files.deleteFile(tempFilePath).catch(err => console.error(`删除临时文件失败: ${err.message}`));
        }
    }
    async function run_ruby_file(params) {
        const filePath = params.file_path;
        if (!filePath || filePath.trim() === "") {
            throw new Error("请提供要执行的 Ruby 文件路径");
        }
        const fileExists = await Tools.Files.exists(filePath);
        if (!fileExists || !fileExists.exists) {
            throw new Error(`Ruby 文件不存在: ${filePath}`);
        }
        const result = await executeTerminalCommand(`ruby ${filePath}`, 30000);
        if (result.exitCode === 0) {
            return result.output.trim();
        }
        else {
            throw new Error(`Ruby 文件执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
        }
    }
    async function run_go(params) {
        const script = params.script;
        if (!script || script.trim() === "") {
            throw new Error("请提供要执行的 Go 代码内容");
        }
        const tempDirPath = "/sdcard/Download/Operit/temp_go";
        const tempFilePath = `${tempDirPath}/main.go`;
        const homeTempBin = "/data/data/com.termux/files/home/temp_go_bin";
        try {
            await executeTerminalCommand(`mkdir -p ${tempDirPath}`, 10000);
            await Tools.Files.write(tempFilePath, script);
            const compileResult = await executeTerminalCommand(`cd ${tempDirPath} && go build -o main main.go`, 30000);
            if (compileResult.exitCode !== 0) {
                throw new Error(`Go 代码编译失败 (退出码: ${compileResult.exitCode}):\n${compileResult.output}`);
            }
            await executeTerminalCommand(`cp ${tempDirPath}/main ${homeTempBin}`, 10000);
            await executeTerminalCommand(`chmod +x ${homeTempBin}`, 10000);
            const result = await executeTerminalCommand(homeTempBin, 30000);
            if (result.exitCode === 0) {
                return result.output.trim();
            }
            else {
                throw new Error(`Go 代码执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
            }
        }
        finally {
            await executeTerminalCommand(`rm -f ${homeTempBin}`, 10000).catch(err => console.error(`删除临时文件失败: ${err.message}`));
            await executeTerminalCommand(`rm -rf ${tempDirPath}`, 10000).catch(err => console.error(`删除临时目录失败: ${err.message}`));
        }
    }
    async function run_go_file(params) {
        const filePath = params.file_path;
        if (!filePath || filePath.trim() === "") {
            throw new Error("请提供要执行的 Go 文件路径");
        }
        const fileExists = await Tools.Files.exists(filePath);
        if (!fileExists || !fileExists.exists) {
            throw new Error(`Go 文件不存在: ${filePath}`);
        }
        const tempExecPath = "/sdcard/Download/Operit/temp_exec";
        const homeTempBin = "/data/data/com.termux/files/home/temp_go_bin";
        try {
            const compileResult = await executeTerminalCommand(`go build -o ${tempExecPath} ${filePath}`, 30000);
            if (compileResult.exitCode !== 0) {
                throw new Error(`Go 文件编译失败 (退出码: ${compileResult.exitCode}):\n${compileResult.output}`);
            }
            await executeTerminalCommand(`cp ${tempExecPath} ${homeTempBin}`, 10000);
            await executeTerminalCommand(`chmod +x ${homeTempBin}`, 10000);
            const result = await executeTerminalCommand(homeTempBin, 30000);
            if (result.exitCode === 0) {
                return result.output.trim();
            }
            else {
                throw new Error(`Go 文件执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
            }
        }
        finally {
            await executeTerminalCommand(`rm -f ${homeTempBin}`, 10000).catch(err => console.error(`删除临时文件失败: ${err.message}`));
            await Tools.Files.deleteFile(tempExecPath).catch(err => console.error(`删除临时文件失败: ${err.message}`));
        }
    }
    async function run_rust(params) {
        const script = params.script;
        if (!script || script.trim() === "") {
            throw new Error("请提供要执行的 Rust 代码内容");
        }
        const tempDirPath = "/data/data/com.termux/files/home/temp_rust_project";
        try {
            const cargoToml = `
[package]
name = "temp_rust_script"
version = "0.1.0"
edition = "2021"

[dependencies]
      `;
            await executeTerminalCommand(`mkdir -p ${tempDirPath}/src`, 10000);
            await executeTerminalCommand(`echo '${cargoToml}' > ${tempDirPath}/Cargo.toml`, 10000);
            await executeTerminalCommand(`echo '${script.replace(/'/g, "'\\''")}' > ${tempDirPath}/src/main.rs`, 10000);
            const compileResult = await executeTerminalCommand(`cd ${tempDirPath} && cargo build --release`, 60000);
            if (compileResult.exitCode !== 0) {
                throw new Error(`Rust 代码编译失败 (退出码: ${compileResult.exitCode}):\n${compileResult.output}`);
            }
            const execPath = `${tempDirPath}/target/release/temp_rust_script`;
            await executeTerminalCommand(`chmod +x ${execPath}`, 10000);
            const result = await executeTerminalCommand(execPath, 30000);
            if (result.exitCode === 0) {
                return result.output.trim();
            }
            else {
                throw new Error(`Rust 代码执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
            }
        }
        finally {
            await executeTerminalCommand(`rm -rf ${tempDirPath}`, 10000).catch(err => console.error(`删除临时目录失败: ${err.message}`));
        }
    }
    async function run_rust_file(params) {
        const filePath = params.file_path;
        if (!filePath || filePath.trim() === "") {
            throw new Error("请提供要执行的 Rust 文件路径");
        }
        const fileExists = await Tools.Files.exists(filePath);
        if (!fileExists || !fileExists.exists) {
            throw new Error(`Rust 文件不存在: ${filePath}`);
        }
        const tempDirPath = "/data/data/com.termux/files/home/temp_rust_project";
        try {
            const cargoToml = `
[package]
name = "temp_rust_script"
version = "0.1.0"
edition = "2021"

[dependencies]
      `;
            await executeTerminalCommand(`mkdir -p ${tempDirPath}/src`, 10000);
            await executeTerminalCommand(`echo '${cargoToml}' > ${tempDirPath}/Cargo.toml`, 10000);
            const fileContent = await Tools.Files.read(filePath);
            if (!fileContent || !fileContent.content) {
                throw new Error(`无法读取文件: ${filePath}`);
            }
            await executeTerminalCommand(`echo '${fileContent.content.replace(/'/g, "'\\''")}' > ${tempDirPath}/src/main.rs`, 10000);
            const compileResult = await executeTerminalCommand(`cd ${tempDirPath} && cargo build --release`, 60000);
            if (compileResult.exitCode !== 0) {
                throw new Error(`Rust 文件编译失败 (退出码: ${compileResult.exitCode}):\n${compileResult.output}`);
            }
            const execPath = `${tempDirPath}/target/release/temp_rust_script`;
            await executeTerminalCommand(`chmod +x ${execPath}`, 10000);
            const result = await executeTerminalCommand(execPath, 30000);
            if (result.exitCode === 0) {
                return result.output.trim();
            }
            else {
                throw new Error(`Rust 项目执行失败 (退出码: ${result.exitCode}):\n${result.output}`);
            }
        }
        finally {
            await executeTerminalCommand(`rm -rf ${tempDirPath}`, 10000).catch(err => console.error(`删除临时目录失败: ${err.message}`));
        }
    }
    function wrap(func) {
        return async (params) => {
            try {
                const result = await func(params);
                complete({
                    success: true,
                    data: result,
                });
            }
            catch (error) {
                complete({
                    success: false,
                    message: error.message,
                    error_stack: error.stack,
                });
            }
        };
    }
    return {
        main,
        run_javascript_es5,
        run_javascript_file,
        run_python,
        run_python_file,
        run_ruby,
        run_ruby_file,
        run_go,
        run_go_file,
        run_rust,
        run_rust_file,
        wrap
    };
})();
// 逐个导出
exports.main = codeRunner.wrap(codeRunner.main);
exports.run_javascript_es5 = codeRunner.wrap(codeRunner.run_javascript_es5);
exports.run_javascript_file = codeRunner.wrap(codeRunner.run_javascript_file);
exports.run_python = codeRunner.wrap(codeRunner.run_python);
exports.run_python_file = codeRunner.wrap(codeRunner.run_python_file);
exports.run_ruby = codeRunner.wrap(codeRunner.run_ruby);
exports.run_ruby_file = codeRunner.wrap(codeRunner.run_ruby_file);
exports.run_go = codeRunner.wrap(codeRunner.run_go);
exports.run_go_file = codeRunner.wrap(codeRunner.run_go_file);
exports.run_rust = codeRunner.wrap(codeRunner.run_rust);
exports.run_rust_file = codeRunner.wrap(codeRunner.run_rust_file);
