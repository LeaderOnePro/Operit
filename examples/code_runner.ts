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

  const CARGO_MIRROR_ENV = 'export CARGO_REGISTRIES_CRATES_IO_REPLACE_WITH="ustc" && export CARGO_REGISTRIES_USTC_INDEX="https://mirrors.ustc.edu.cn/crates.io-index"';

  // Helper function to execute a terminal command using the new session-based API
  async function executeTerminalCommand(command: string, timeoutMs?: number): Promise<import("./types/results").TerminalCommandResultData> {
    // Use a consistent session name to allow for session reuse
    const session = await Tools.System.terminal.create("code_runner_session");
    return await Tools.System.terminal.exec(session.sessionId, command, timeoutMs);
  }

  // Helper function to safely escape strings for shell commands
  function escapeForShell(str: string): string {
    return str.replace(/'/g, "'\\''");
  }

  // Helper function to check for errors in command output when exit code is unreliable
  function hasError(output: string): boolean {
    const errorPatterns = [
      "command not found",
      "No such file or directory",
      "error:",
      "Error:",
      "failed",
      "Failed",
      "unable",
      "Unable"
    ];
    const lowercasedOutput = output.toLowerCase();
    return errorPatterns.some(pattern => lowercasedOutput.includes(pattern));
  }

  async function main() {
    // Ensure /tmp exists
    await executeTerminalCommand("mkdir -p /tmp");

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
    } catch (error) {
      return { success: false, message: `JavaScript执行器测试失败: ${error.message}` };
    }
  }

  // 测试Python执行功能  
  async function testPython() {
    try {
      // 检查Python是否可用
      const pythonCheckResult = await executeTerminalCommand("python3 --version", 10000);
      if (pythonCheckResult.exitCode !== 0 || hasError(pythonCheckResult.output)) {
        return { success: false, message: "Python不可用，请确保已安装Python" };
      }

      // 测试简单的Python代码
      const script = "print('Python运行正常')";
      const tempPyFile = "/tmp/test_python.py";
      await executeTerminalCommand(`cat <<'EOF' > ${tempPyFile}\n${script}\nEOF`);
      const runResult = await executeTerminalCommand(`python3 ${tempPyFile}`, 10000);
      await executeTerminalCommand(`rm -f ${tempPyFile}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("Python运行正常")) {
        return { success: false, message: `Python执行器测试失败: ${runResult.output}` };
      }
      return { success: true, message: "Python执行器测试成功" };
    } catch (error) {
      return { success: false, message: `Python执行器测试失败: ${error.message}` };
    }
  }

  // 测试Ruby执行功能
  async function testRuby() {
    try {
      // 检查Ruby是否可用
      const rubyCheckResult = await executeTerminalCommand("ruby --version", 10000);
      if (rubyCheckResult.exitCode !== 0 || hasError(rubyCheckResult.output)) {
        return { success: false, message: "Ruby不可用，请确保已安装Ruby" };
      }

      // 测试简单的Ruby代码
      const script = "puts 'Ruby运行正常'";
      const tempRbFile = "/tmp/test_ruby.rb";
      await executeTerminalCommand(`cat <<'EOF' > ${tempRbFile}\n${script}\nEOF`);
      const runResult = await executeTerminalCommand(`ruby ${tempRbFile}`, 10000);
      await executeTerminalCommand(`rm -f ${tempRbFile}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("Ruby运行正常")) {
        return { success: false, message: `Ruby执行器测试失败: ${runResult.output}` };
      }
      return { success: true, message: "Ruby执行器测试成功" };
    } catch (error) {
      return { success: false, message: `Ruby执行器测试失败: ${error.message}` };
    }
  }

  // 测试Go执行功能
  async function testGo() {
    try {
      // 检查Go是否可用
      const goCheckResult = await executeTerminalCommand("go version", 10000);
      if (goCheckResult.exitCode !== 0 || hasError(goCheckResult.output)) {
        return { success: false, message: "Go不可用，请确保已安装Go" };
      }

      // 测试简单的Go代码
      const script = `
package main
import "fmt"
func main() {
  fmt.Println("Go运行正常")
}`;
      const tempGoDir = "/tmp/test_go_project";
      const tempGoFile = `${tempGoDir}/main.go`;
      const tempGoExec = `${tempGoDir}/main`;
      await executeTerminalCommand(`mkdir -p ${tempGoDir}`);
      await executeTerminalCommand(`cat <<'EOF' > ${tempGoFile}\n${script}\nEOF`);

      const compileResult = await executeTerminalCommand(`cd ${tempGoDir} && go build -o main main.go`, 30000);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        await executeTerminalCommand(`rm -rf ${tempGoDir}`);
        return { success: false, message: `Go 编译失败: ${compileResult.output}` };
      }

      const runResult = await executeTerminalCommand(tempGoExec, 10000);
      await executeTerminalCommand(`rm -rf ${tempGoDir}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("Go运行正常")) {
        return { success: false, message: `Go 执行失败: ${runResult.output}` };
      }

      return { success: true, message: "Go执行器测试成功" };
    } catch (error) {
      return { success: false, message: `Go执行器测试失败: ${error.message}` };
    }
  }

  // 检查并配置Rust环境
  async function ensureRustConfigured(): Promise<{ success: boolean; message: string }> {
    // 在有效目录中运行，避免 "Could not locate working directory" 错误
    let rustCheckResult = await executeTerminalCommand("cd /tmp && rustc --version", 10000);

    if (rustCheckResult.exitCode === 0 && !hasError(rustCheckResult.output)) {
      return { success: true, message: "Rust环境已配置" };
    }

    // 如果未配置默认工具链，则尝试设置
    if (rustCheckResult.output.includes("no default is configured")) {
      const setupResult = await executeTerminalCommand('export RUSTUP_DIST_SERVER="https://mirrors.ustc.edu.cn/rust-static" && export RUSTUP_UPDATE_ROOT="https://mirrors.ustc.edu.cn/rust-static/rustup" && rustup default stable', 60000);
      if (setupResult.exitCode !== 0 || hasError(setupResult.output)) {
        return { success: false, message: `运行 'rustup default stable' 失败: ${setupResult.output}` };
      }

      // 再次检查
      rustCheckResult = await executeTerminalCommand("cd /tmp && rustc --version", 10000);
      if (rustCheckResult.exitCode === 0 && !hasError(rustCheckResult.output)) {
        return { success: true, message: "Rust环境已自动配置" };
      }
    }

    return { success: false, message: `Rust环境检查失败: ${rustCheckResult.output}` };
  }

  // 测试Rust执行功能
  async function testRust() {
    try {
      const rustConfig = await ensureRustConfigured();
      if (!rustConfig.success) {
        return { success: false, message: rustConfig.message };
      }

      // 测试简单的Rust代码
      const script = `
fn main() {
  println!("Rust运行正常");
}`;
      const tempRustDir = "/tmp/test_rust_project";
      const tempRustSrcDir = `${tempRustDir}/src`;
      const tempRustFile = `${tempRustSrcDir}/main.rs`;
      const cargoToml = `
[package]
name = "test_rust"
version = "0.1.0"
edition = "2021"
[dependencies]
`;
      await executeTerminalCommand(`mkdir -p ${tempRustSrcDir}`);
      await executeTerminalCommand(`cat <<'EOF' > ${tempRustDir}/Cargo.toml\n${cargoToml}\nEOF`);
      await executeTerminalCommand(`cat <<'EOF' > ${tempRustFile}\n${script}\nEOF`);

      // 在有效目录中运行 cargo
      const compileResult = await executeTerminalCommand(`cd ${tempRustDir} && ${CARGO_MIRROR_ENV} && cargo build --release`, 60000);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        await executeTerminalCommand(`rm -rf ${tempRustDir}`);
        return { success: false, message: `Rust 编译失败: ${compileResult.output}` };
      }

      const execPath = `${tempRustDir}/target/release/test_rust`;
      const runResult = await executeTerminalCommand(execPath, 30000);
      await executeTerminalCommand(`rm -rf ${tempRustDir}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("Rust运行正常")) {
        return { success: false, message: `Rust 执行失败: ${runResult.output}` };
      }

      return { success: true, message: "Rust执行器测试成功" };
    } catch (error) {
      return { success: false, message: `Rust执行器测试失败: ${error.message}` };
    }
  }

  async function run_javascript_es5(params: { script: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的脚本内容");
    }
    // Wrap in a function to allow return statements
    return eval(`(function(){${script}})()`);
  }

  async function run_javascript_file(params: { file_path: string }) {
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

  async function run_python(params: { script: string }) {
    const script = params.script;

    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 Python 脚本内容");
    }

    const tempFilePath = "/tmp/temp_script.py";
    try {
      await executeTerminalCommand(`cat <<'EOF' > ${tempFilePath}\n${script}\nEOF`);
      const result = await executeTerminalCommand(`python3 ${tempFilePath}`, 30000);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Python 脚本执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -f ${tempFilePath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }

  async function run_python_file(params: { file_path: string }) {
    const filePath = params.file_path;

    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 Python 文件路径");
    }

    const fileExistsResult = await executeTerminalCommand(`test -f ${filePath}`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`Python 文件不存在或路径错误: ${filePath}`);
    }

    const result = await executeTerminalCommand(`python3 ${filePath}`, 30000);
    if (result.exitCode === 0 && !hasError(result.output)) {
      return result.output.trim();
    } else {
      throw new Error(`Python 文件执行失败:\n${result.output}`);
    }
  }

  async function run_ruby(params: { script: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 Ruby 脚本内容");
    }

    const tempFilePath = "/tmp/temp_script.rb";
    try {
      await executeTerminalCommand(`cat <<'EOF' > ${tempFilePath}\n${script}\nEOF`);
      const result = await executeTerminalCommand(`ruby ${tempFilePath}`, 30000);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Ruby 脚本执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -f ${tempFilePath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }

  async function run_ruby_file(params: { file_path: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 Ruby 文件路径");
    }

    const fileExistsResult = await executeTerminalCommand(`test -f ${filePath}`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`Ruby 文件不存在或路径错误: ${filePath}`);
    }

    const result = await executeTerminalCommand(`ruby ${filePath}`, 30000);
    if (result.exitCode === 0 && !hasError(result.output)) {
      return result.output.trim();
    } else {
      throw new Error(`Ruby 文件执行失败:\n${result.output}`);
    }
  }

  async function run_go(params: { script: string }) {
    const script = params.script;

    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 Go 代码内容");
    }

    const tempDirPath = "/tmp/temp_go";
    const tempFilePath = `${tempDirPath}/main.go`;

    try {
      await executeTerminalCommand(`mkdir -p ${tempDirPath}`, 10000);
      await executeTerminalCommand(`cat <<'EOF' > ${tempFilePath}\n${script}\nEOF`);

      const compileResult = await executeTerminalCommand(`cd ${tempDirPath} && go build -o main main.go`, 30000);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`Go 代码编译失败:\n${compileResult.output}`);
      }

      const result = await executeTerminalCommand(`${tempDirPath}/main`, 30000);

      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Go 代码执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -rf ${tempDirPath}`, 10000).catch(err => console.error(`删除临时目录失败: ${err.message}`));
    }
  }

  async function run_go_file(params: { file_path: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 Go 文件路径");
    }

    const fileExistsResult = await executeTerminalCommand(`test -f ${filePath}`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`Go 文件不存在或路径错误: ${filePath}`);
    }

    const tempExecPath = "/tmp/temp_go_exec";
    try {
      const compileResult = await executeTerminalCommand(`go build -o ${tempExecPath} ${filePath}`, 30000);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`Go 文件编译失败:\n${compileResult.output}`);
      }

      const result = await executeTerminalCommand(tempExecPath, 30000);

      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Go 文件执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -f ${tempExecPath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }

  async function run_rust(params: { script: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 Rust 代码内容");
    }

    const rustConfig = await ensureRustConfigured();
    if (!rustConfig.success) {
      throw new Error(rustConfig.message);
    }

    const tempDirPath = "/tmp/temp_rust_project";
    try {
      const cargoToml = `
[package]
name = "temp_rust_script"
version = "0.1.0"
edition = "2021"

[dependencies]
      `;
      await executeTerminalCommand(`mkdir -p ${tempDirPath}/src`, 10000);
      await executeTerminalCommand(`cat <<'EOF' > ${tempDirPath}/Cargo.toml\n${cargoToml}\nEOF`);
      await executeTerminalCommand(`cat <<'EOF' > ${tempDirPath}/src/main.rs\n${script}\nEOF`);

      const compileResult = await executeTerminalCommand(`cd ${tempDirPath} && ${CARGO_MIRROR_ENV} && cargo build --release`, 60000);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`Rust 代码编译失败:\n${compileResult.output}`);
      }

      const execPath = `${tempDirPath}/target/release/temp_rust_script`;
      const result = await executeTerminalCommand(execPath, 30000);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Rust 代码执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -rf ${tempDirPath}`, 10000).catch(err => console.error(`删除临时目录失败: ${err.message}`));
    }
  }

  async function run_rust_file(params: { file_path: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 Rust 文件路径");
    }
    const fileExistsResult = await executeTerminalCommand(`test -f ${filePath}`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`Rust 文件不存在或路径错误: ${filePath}`);
    }

    const rustConfig = await ensureRustConfigured();
    if (!rustConfig.success) {
      throw new Error(rustConfig.message);
    }

    const tempDirPath = "/tmp/temp_rust_project";
    try {
      const cargoToml = `
[package]
name = "temp_rust_script"
version = "0.1.0"
edition = "2021"

[dependencies]
      `;
      await executeTerminalCommand(`mkdir -p ${tempDirPath}/src`, 10000);
      await executeTerminalCommand(`cat <<'EOF' > ${tempDirPath}/Cargo.toml\n${cargoToml}\nEOF`);

      const readResult = await executeTerminalCommand(`cat ${filePath}`);
      if (readResult.exitCode !== 0 || hasError(readResult.output)) {
        throw new Error(`无法读取文件: ${filePath}`);
      }
      const fileContent = readResult.output;
      await executeTerminalCommand(`cat <<'EOF' > ${tempDirPath}/src/main.rs\n${fileContent}\nEOF`);

      const compileResult = await executeTerminalCommand(`cd ${tempDirPath} && ${CARGO_MIRROR_ENV} && cargo build --release`, 60000);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`Rust 文件编译失败:\n${compileResult.output}`);
      }

      const execPath = `${tempDirPath}/target/release/temp_rust_script`;
      const result = await executeTerminalCommand(execPath, 30000);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Rust 项目执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -rf ${tempDirPath}`, 10000).catch(err => console.error(`删除临时目录失败: ${err.message}`));
    }
  }

  function wrap(func: (params: any) => Promise<any>) {
    return async (params: any) => {
      try {
        const result = await func(params);
        complete({
          success: true,
          data: result,
        });
      } catch (error: any) {
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