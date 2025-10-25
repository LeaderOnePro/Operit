# **Android 项目 Operit 编译指南（Linux/Ubuntu）**

本指南详细介绍了在 Linux 环境下（推荐 Ubuntu/Debian）编译 Android 项目 **Operit** 所需的全部环境配置和步骤。

## **关于 Operit**

**Operit AI** 是移动端首个功能完备的 AI 智能助手应用，它**完全独立运行**于您的 Android 设备上，拥有强大的**工具调用能力**。本项目旨在为开发者提供一个可深度定制和扩展的 AI 助手框架。

在开始编译之前，请确保您已了解本项目的功能和目标。更多信息请参考项目主页的 [README.md](../README.md)。

## **目录**

1. 第一步：安装系统基础依赖
2. 第二步：安装 Android 命令行工具
3. 第三步：配置环境变量
4. 第四步：安装 Android SDK 和 NDK
5. 附：性能优化 - 配置编译资源
6. 第五步：克隆并编译项目
7. 常见问题排查

## **1. 第一步：安装系统基础依赖**

首先，我们需要更新包管理器并安装编译所需的关键基础软件：**Git** 和 **JDK 17**。  
```bash 
# 更新软件包列表  
sudo apt update

# 安装必要的工具和 JDK 17  
sudo apt install -y git wget unzip openjdk-17-jdk

安装完成后，请验证 Java 版本是否正确：  
java -version  
# 预期输出应包含 "OpenJDK Runtime Environment (build 17..." 或类似信息
``` 
**注意：** 项目官方要求 **JDK 17**。为确保最大兼容性，强烈建议优先安装和使用 JDK 17。

## **2. 第二步：安装 Android 命令行工具**

为了管理 SDK，我们将使用更轻量的 Android 命令行工具（Command Line Tools），而非庞大的 Android Studio。

1. **创建 Android SDK 目录:**  
```bash
mkdir -p ~/Android/cmdline-tools
```
2. 下载命令行工具:  
访问 Android Developers 官网，复制最新的 Linux 版本链接。  
**警告：** 下方的链接仅为示例，请务必检查并替换为官方提供的最新链接！  
```bash
# 示例链接，请务必检查并替换为最新版本  
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O ~/cmdline-tools.zip
```

3. 解压并配置目录结构:  
命令行工具要求其文件位于一个名为 latest 的子目录中，否则 sdkmanager 可能无法识别。  
```bash
# 解压到目标目录  
unzip ~/cmdline-tools.zip -d ~/Android/cmdline-tools

# 将解压后的 cmdline-tools 移动到 latest 子目录  
mv ~/Android/cmdline-tools/cmdline-tools ~/Android/cmdline-tools/latest

# 清理下载的压缩包  
rm ~/cmdline-tools.zip
```
最终的工具路径应为 ~/Android/cmdline-tools/latest/bin。

## **3. 第三步：配置环境变量**

配置环境变量以便系统能找到 **Java** 和 **Android SDK** 的相关命令，如 java、git 和 sdkmanager。

1. **编辑配置文件：**  
```bash
nano ~/.bashrc
```

2. **在文件末尾添加以下内容：**  
```bash
# =============== Java JDK 17 配置 ===============  
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64  
export PATH=$JAVA_HOME/bin:$PATH

# =============== Android SDK 配置 ===============  
export ANDROID_HOME=$HOME/Android  
# 将 latest/bin 添加到 PATH  
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH  
# 将 platform-tools (ADB/Fastboot) 添加到 PATH  
export PATH=$ANDROID_HOME/platform-tools:$PATH
```

3. **使配置生效:**  
```bash
source ~/.bashrc
```

## **4. 第四步：安装 Android SDK 和 NDK**

使用刚才配置好的 sdkmanager 命令来安装项目所需的 SDK 平台、构建工具和特定版本的 NDK。

1. 接受所有 SDK 许可 (关键步骤！):  
此步骤是必须的，否则 Gradle 构建会因许可问题而失败。  
```bash
yes | sdkmanager --licenses
```

2. 安装平台工具、SDK 平台和构建工具:  
Operit 项目依赖于 android-34 平台和 34.0.0 构建工具。  
```bash
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```
3. 安装项目指定的 NDK 版本:  
本项目要求使用 NDK 25.1.8937393。  
```bash
sdkmanager "ndk;25.1.8937393"
```

## **附：性能优化 - 配置编译资源**

对于配置较高的机器（如 16GB 内存或以上），可以通过调整 Gradle 配置来显著加快编译速度。  
在项目根目录下的 **gradle.properties** 文件中，您可以调整以下参数：  

```properties
# 设置 Gradle 使用的 JVM 最大内存，例如 8GB  
org.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=1g -XX:+HeapDumpOnOutOfMemoryError

# 开启并行编译  
org.gradle.parallel=true

# (可选) 设置并行编译的 worker 数量，通常建议设置为 CPU 核心数  
# org.gradle.workers.max=8
``` 

## **5. 第五步：克隆并编译项目**

环境准备就绪，现在开始编译项目。

1. 克隆项目仓库并进入目录:  
请务必将 你的用户名 替换为你在 GitHub 上的实际用户名。  
```bash
git clone https://github.com/你的用户名/Operit.git
cd Operit
```
2. **下载并放置依赖库 (关键步骤！):**
   `README.md` 中提到，项目依赖一些需要手动下载的库。请从 [这个 Google Drive 链接](https://drive.google.com/drive/folders/1g-Q_i7cf6Ua4KX9ZM6V282EEZvTVVfF7?usp=sharing) 下载所有文件，并将它们解压或放置到项目根目录下对应的 `libs` 或有 `.keep` 文件的文件夹中。
   **警告：** 如果跳过此步骤，编译将因缺少依赖而失败。下载到的三个压缩包解压覆盖这三个文件夹就可以了。
```bash
./app/src/main/assets/models/.keep  
./app/src/main/assets/subpack/.keep  
./app/src/main/jniLibs/.keep
```

3. **切换到你的工作分支 (如果需要):**
```bash
git checkout docs/add-building-guide
# 将上面的示例分支名替换为你自己创建的分支名
```

4. **为 Gradle 包装器添加可执行权限:**
```bash
chmod +x ./gradlew
```

4. 运行 assembleDebug 命令进行编译:  
首次编译会下载大量依赖，请耐心等待。  
```bash
./gradlew assembleDebug
```
5. 查找 APK 文件:  
编译成功后，生成的 APK 文件位于项目目录下的以下路径：  
app/build/outputs/apk/debug/app-debug.apk

## **6. 常见问题排查**

| 错误信息 | 解决方案 |
| :---- | :---- |
| sdkmanager: command not found | 环境变量未正确设置或生效。请检查 **~/.bashrc** 文件内容，并执行 source ~/.bashrc。 |
| Could not determine Java version... | **JAVA_HOME** 环境变量不正确，或安装了错误的 JDK 版本。请确保已安装 **JDK 17** 并指向正确的路径。 |
| NDK not found. | 确保已在 **第四步** 中使用 sdkmanager 安装了项目所需的 **ndk;25.1.8937393** 版本。 |
| You have not accepted the license agreements... | 你跳过了或未成功执行接受许可的步骤。请返回 **第四步** 执行 `yes |

