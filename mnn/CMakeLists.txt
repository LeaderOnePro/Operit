cmake_minimum_required(VERSION 3.22.1)

project("mnn_jni")

# 设置 MNN 源代码路径
set(MNN_SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/MNN)

# 添加 MNN 主项目
add_subdirectory(${MNN_SOURCE_DIR} ${CMAKE_CURRENT_BINARY_DIR}/MNN)

# 包含 JNI 绑定代码（只包含我们需要的文件）
add_library(
    MNNWrapper
    SHARED
    src/main/cpp/mnnnetnative.cpp
    src/main/cpp/mnnmodulennative.cpp
    # mnnportraitnative.cpp 使用的是不同的包名，这里不需要
)

# 包含 MNN 头文件
target_include_directories(MNNWrapper PRIVATE
    ${MNN_SOURCE_DIR}/include
    ${MNN_SOURCE_DIR}/source
    ${MNN_SOURCE_DIR}/express
)

# 添加编译选项以解决 TLS 问题
target_compile_options(MNNWrapper PRIVATE -fno-emulated-tls)
target_compile_options(MNN PRIVATE -fno-emulated-tls)

# 链接 MNN 库
target_link_libraries(
    MNNWrapper
    MNN
    android
    log
    jnigraphics
)

