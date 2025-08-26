/*
METADATA
{
    // 小红书智能助手包
    name: "Automatic_xiaohongshu_assistant",
    description: "高级小红书智能助手，通过UI自动化技术实现小红书应用交互，支持内容浏览、搜索查看、评论互动、内容发布等功能，为AI赋予小红书社交和内容创作能力。适用于内容营销、社交互动、生活分享等场景。",
    
    // Tools in this package
    tools: [
        {
            name: "workflow_guide",
            description: "小红书助手工具使用流程指南。要完成复杂任务，请按以下顺序组合使用工具：\n1. **浏览主页**: 使用 `browse_home_feed` 刷新并浏览主页内容，这是了解平台动态的起点。\n2. **搜索内容**: 使用 `search_content` 查找特定主题的帖子或用户。\n3. **查看帖子**: 从搜索结果或主页中，使用 `view_post` 查看具体帖子详情。\n4. **互动操作**: 查看帖子后，可执行 `like_post`, `collect_post`, `follow_user`, `comment_post` 等互动。\n5. **发布内容**: 使用 `publish_post` 发布新的图片或视频帖子。\n6. **返回导航**: `navigate_to_home` 等导航工具可在多数情况下使用。",
            parameters: []
        },
        {
            name: "browse_home_feed",
            description: "浏览小红书主页信息流，获取推荐内容",
            parameters: [
                {
                    name: "scroll_count",
                    description: "滚动次数，控制浏览的内容量，默认为3次",
                    type: "number",
                    required: false
                },
                {
                    name: "collect_posts",
                    description: "是否收集帖子信息，默认为true",
                    type: "boolean",
                    required: false
                }
            ]
        },
        {
            name: "search_content",
            description: "在小红书中搜索内容、用户或话题",
            parameters: [
                {
                    name: "keyword",
                    description: "搜索关键词",
                    type: "string",
                    required: true
                },
                {
                    name: "search_type",
                    description: "搜索类型：comprehensive(综合)、note(笔记)、user(用户)、topic(话题)",
                    type: "string",
                    required: false
                }
            ]
        },
        {
            name: "view_post",
            description: "查看指定的帖子详情",
            parameters: [
                {
                    name: "post_title",
                    description: "要查看的帖子标题关键词",
                    type: "string",
                    required: false
                },
                {
                    name: "post_index",
                    description: "要查看的帖子在列表中的索引位置（从1开始）",
                    type: "number",
                    required: false
                }
            ]
        },
        {
            name: "like_post",
            description: "给当前帖子点赞",
            parameters: []
        },
        {
            name: "collect_post",
            description: "收藏当前帖子",
            parameters: [
                {
                    name: "collection_name",
                    description: "收藏夹名称，留空则使用默认收藏夹",
                    type: "string",
                    required: false
                }
            ]
        },
        {
            name: "follow_user",
            description: "关注当前帖子的作者",
            parameters: []
        },
        {
            name: "comment_post",
            description: "在当前帖子下发表评论",
            parameters: [
                {
                    name: "comment_text",
                    description: "要发表的评论内容",
                    type: "string",
                    required: true
                }
            ]
        },
        {
            name: "get_post_info",
            description: "获取当前帖子的详细信息，包括标题、作者、点赞数、评论数等",
            parameters: []
        },
        {
            name: "browse_comments",
            description: "浏览当前帖子的评论，获取热门评论内容",
            parameters: [
                {
                    name: "comment_count",
                    description: "获取的评论数量，默认为5条",
                    type: "number",
                    required: false
                }
            ]
        },
        {
            name: "publish_post",
            description: "发布新的帖子内容",
            parameters: [
                {
                    name: "content_text",
                    description: "帖子文字内容",
                    type: "string",
                    required: true
                },
                {
                    name: "image_paths",
                    description: "图片文件路径列表，用逗号分隔",
                    type: "string",
                    required: false
                },
                {
                    name: "tags",
                    description: "话题标签，用逗号分隔",
                    type: "string",
                    required: false
                },
                {
                    name: "location",
                    description: "地理位置信息",
                    type: "string",
                    required: false
                }
            ]
        },
        {
            name: "navigate_to_home",
            description: "导航到小红书首页",
            parameters: []
        },
        {
            name: "navigate_to_profile",
            description: "导航到个人主页",
            parameters: []
        },
        {
            name: "navigate_to_search",
            description: "导航到搜索页面",
            parameters: []
        },
        {
            name: "navigate_to_publish",
            description: "导航到发布页面",
            parameters: []
        },
        {
            name: "back_to_feed",
            description: "从帖子详情页返回到信息流列表",
            parameters: []
        }
    ],
    "category": "UI_AUTOMATION"
}
*/
var __rest = (this && this.__rest) || function (s, e) {
    var t = {};
    for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0)
        t[p] = s[p];
    if (s != null && typeof Object.getOwnPropertySymbols === "function")
        for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
            if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i]))
                t[p[i]] = s[p[i]];
        }
    return t;
};
const XiaohongshuAssistant = (function () {
    // 添加 Array.prototype.at 支持
    Array.prototype.at = function (index) {
        if (index < 0) {
            index = this.length + index;
        }
        return this[index];
    };
    // 小红书应用包名和主要Activity
    const XIAOHONGSHU_PACKAGE = "com.xingin.xhs";
    const MAIN_ACTIVITY = "com.xingin.xhs.activity.SplashActivity";
    const HOME_ACTIVITY = "com.xingin.xhs.index.v2.IndexActivityV2";
    // Helper to create response objects
    function createResponse(success, message, data = {}) {
        if (typeof data === 'string') {
            return { success: true, message: message, data: data };
        }
        return Object.assign({ success, message }, data);
    }
    // Helper to find a UI element and click it
    async function findAndClick(finder) {
        const element = await finder();
        if (element) {
            await element.click();
            await Tools.System.sleep(1000);
            return true;
        }
        return false;
    }
    // Helper to find a UI element by text and click it
    async function findAndClickByText(text) {
        return findAndClick(async () => (await UINode.getCurrentPage()).findByText(text));
    }
    async function ensureMain(packageName = XIAOHONGSHU_PACKAGE) {
        let pageInfo = await Tools.UI.getPageInfo();
        // 1. 如果不在小红书app，则启动
        if (pageInfo.packageName !== packageName) {
            await Tools.System.startApp(packageName);
            await Tools.System.sleep(3000);
            pageInfo = await Tools.UI.getPageInfo();
        }
        // 2. 检查是否在首页
        const isAlreadyOnHome = async () => {
            const page = await UINode.getCurrentPage();
            const followButton = page.findByContentDesc("关注");
            const discoverButton = page.findByContentDesc("发现");
            const searchButton = page.findByContentDesc("搜索");
            return followButton && discoverButton && searchButton;
        };
        if (await isAlreadyOnHome()) {
            console.log("已经在首页。");
            return true;
        }
        // 3. 尝试通过返回回到首页
        console.log("尝试返回到首页。");
        for (let i = 0; i < 4; i++) {
            await Tools.UI.pressKey("KEYCODE_BACK");
            await Tools.System.sleep(1000);
            if (await isAlreadyOnHome()) {
                console.log("成功回到首页。");
                return true;
            }
            pageInfo = await Tools.UI.getPageInfo();
            if (pageInfo.packageName !== packageName) {
                console.log("返回时退出了app。");
                break; // Exit loop if we've exited the app
            }
        }
        // 4. 如果返回失败，重启app
        console.log("返回失败，重启app。");
        await Tools.System.stopApp(packageName);
        await Tools.System.sleep(1000);
        await Tools.System.startApp(packageName);
        await Tools.System.sleep(5000);
        pageInfo = await Tools.UI.getPageInfo();
        if (pageInfo.packageName === packageName) {
            console.log("App重启成功。");
            return true;
        }
        console.error(`无法导航到 ${packageName} 的首页。`);
        return false;
    }
    async function scrollToTop(maxSwipes = 5) {
        console.log("Scrolling to top...");
        for (let i = 0; i < maxSwipes; i++) {
            // Swipe down to scroll the list up to the top
            await Tools.UI.swipe(540, 800, 540, 1600);
            await Tools.System.sleep(500);
        }
        await Tools.System.sleep(1000);
        console.log("Finished scrolling to top.");
    }
    // --- 小红书自动化逻辑 ---
    let lastBrowseResults = [];
    async function browse_home_feed(params) {
        const { scroll_count = 5, collect_posts = false } = params;
        const posts = [];
        console.log(`开始浏览, 滑动次数: ${scroll_count}, 是否收集帖子: ${collect_posts}`);
        for (let i = 0; i < scroll_count; i++) {
            console.log(`第 ${i + 1} 次滑动...`);
            if (collect_posts) {
                const currentPage = await UINode.getCurrentPage();
                const searchScopeNode = currentPage;
                console.log("将在整个页面范围内查找帖子。");
                // 为了调试，我们先找到所有 FrameLayout 并打印它们的 contentDesc
                const allFrameLayouts = searchScopeNode.findAll(n => n.className === 'FrameLayout');
                console.log(`[调试] 在查找范围内找到了 ${allFrameLayouts.length} 个 FrameLayout 节点。`);
                for (const frame of allFrameLayouts) {
                    if (frame.contentDesc) {
                        console.log(`[调试] FrameLayout contentDesc: "${frame.contentDesc}"`);
                    }
                }
                // 正式筛选帖子节点
                const postNodes = allFrameLayouts.filter(n => n.contentDesc && n.contentDesc.includes(' 来自'));
                console.log(`本次查找共发现 ${postNodes.length} 个帖子。`);
                for (const postNode of postNodes) {
                    const desc = postNode.contentDesc;
                    if (desc) {
                        try {
                            const parts = desc.split(' 来自');
                            if (parts.length > 1) {
                                const authorAndLikesStr = parts.pop().trim();
                                let title = parts.join(' 来自').trim();
                                // 移除 "笔记" or "视频" 前缀, 包括中间有空格或换行的情况
                                title = title.replace(/^(?:笔\s*记|视\s*频)\s*/, '');
                                const words = authorAndLikesStr.split(/\s+/);
                                if (words.length > 0) {
                                    const likesWithSuffix = words.pop();
                                    const author = words.join(' ');
                                    if (title && likesWithSuffix && likesWithSuffix.endsWith('赞')) {
                                        const post = {
                                            title,
                                            author: author || "未知作者",
                                            likes: likesWithSuffix.replace('赞', '').trim()
                                        };
                                        if (!posts.some(p => p.title === post.title && p.author === post.author)) {
                                            console.log(` -> 收集到新帖子: ${post.title} | 作者: ${post.author} | 赞: ${post.likes}`);
                                            posts.push(post);
                                        }
                                    }
                                }
                            }
                        }
                        catch (e) {
                            console.log(`解析时出现异常: "${desc}"`, e);
                        }
                    }
                }
            }
            await Tools.UI.swipe(500, 1800, 500, 500); // 从下往上滑动
            await Tools.System.sleep(2500); // 等待新内容加载
        }
        console.log(`浏览结束, 共收集到 ${posts.length} 个独立帖子。`);
        return createResponse(true, "成功浏览主页信息流", { posts: posts });
    }
    async function search_content(params) {
        const { keyword, search_type = "comprehensive" } = params;
        console.log(`搜索内容: ${keyword}, 类型: ${search_type}`);
        // 确保小红书已在前台运行
        if (!await ensureMain()) {
            return createResponse(false, "无法启动或切换到小红书");
        }
        // 1. 点击主页右上角的搜索图标
        let page = await UINode.getCurrentPage();
        const searchIcon = page.findByContentDesc("搜索");
        if (!searchIcon) {
            return createResponse(false, "在主页上找不到带 '搜索' contentDesc 的图标");
        }
        await searchIcon.click();
        await Tools.System.sleep(2000); // 等待搜索页面加载
        // 2. 在搜索页面，找到输入框并输入文字
        page = await UINode.getCurrentPage();
        const searchInput = page.findByClass("EditText");
        if (!searchInput) {
            return createResponse(false, "在搜索页面找不到输入框");
        }
        await searchInput.click();
        await Tools.System.sleep(1000);
        await Tools.UI.setText(keyword);
        await Tools.System.sleep(500);
        // 3. 点击“搜索”按钮
        page = await UINode.getCurrentPage();
        const searchButtons = page.findAllByText("搜索");
        const searchButton = searchButtons.at(-1); // 通常，实际的搜索按钮是最后一个
        if (!searchButton) {
            console.log("找不到文本为'搜索'的按钮，尝试按回车键");
            await Tools.UI.pressKey("KEYCODE_ENTER");
        }
        else {
            await searchButton.click();
        }
        await Tools.System.sleep(3000); // 等待搜索结果加载
        // 应用搜索类型过滤
        if (search_type !== "comprehensive") {
            await applySearchFilter(search_type);
        }
        // 获取搜索结果
        const results = await getSearchResults();
        return createResponse(true, `搜索到 ${results.length} 条相关内容`, {
            keyword: keyword,
            search_type: search_type,
            results: results.map((_a) => {
                var { element } = _a, rest = __rest(_a, ["element"]);
                return rest;
            }), // 移除element属性
            result_count: results.length
        });
    }
    async function applySearchFilter(filterType) {
        console.log(`应用搜索过滤器: ${filterType}`);
        const page = await UINode.getCurrentPage();
        let filterButton;
        switch (filterType) {
            case "note":
                filterButton = page.findByText("笔记");
                break;
            case "user":
                filterButton = page.findByText("用户");
                break;
            case "topic":
                filterButton = page.findByText("话题");
                break;
        }
        if (filterButton) {
            await filterButton.click();
            await Tools.System.sleep(2000);
        }
    }
    function extractPostInfoFromNode(node, index) {
        var _a, _b;
        const allTextViews = node.findAllByClass("TextView");
        if (allTextViews.length < 2)
            return null; // 需要标题和其他信息
        // 1. 寻找标题：通常是当中最长的文本
        const sortedByLength = [...allTextViews].sort((a, b) => { var _a, _b; return (((_a = b.text) === null || _a === void 0 ? void 0 : _a.length) || 0) - (((_b = a.text) === null || _b === void 0 ? void 0 : _b.length) || 0); });
        const titleNode = sortedByLength[0];
        if (!titleNode || !titleNode.text || titleNode.text.length < 5)
            return null;
        const title = titleNode.text;
        const otherTextViews = allTextViews.filter(tv => tv !== titleNode);
        // 2. 寻找点赞数：包含数字，可能带“万”
        const likesRegex = /^\s*\d+(\.\d+)?\s*万?\s*$/;
        const likesNode = otherTextViews.find(tv => tv.text && likesRegex.test(tv.text.trim()));
        const likes = ((_a = likesNode === null || likesNode === void 0 ? void 0 : likesNode.text) === null || _a === void 0 ? void 0 : _a.trim()) || "未知";
        // 3. 寻找作者：不是点赞数，也不是日期
        const dateRegex = /^\d{2,4}-\d{2}(-\d{2})?$/;
        const authorNode = otherTextViews.find(tv => tv.text &&
            tv !== likesNode &&
            !dateRegex.test(tv.text.trim()));
        const author = ((_b = authorNode === null || authorNode === void 0 ? void 0 : authorNode.text) === null || _b === void 0 ? void 0 : _b.trim()) || "未知作者";
        if (title === author) {
            return null;
        }
        return {
            index,
            title,
            author,
            likes,
            element: node,
        };
    }
    async function getSearchResults(desiredCount = 15) {
        const results = [];
        const seenTitles = new Set();
        const maxScrolls = 5;
        let lastResultCount = -1;
        for (let i = 0; i < maxScrolls && results.length < desiredCount; i++) {
            const page = await UINode.getCurrentPage();
            const resultsContainer = page.findByClass("RecyclerView");
            if (!resultsContainer) {
                console.log("未找到搜索结果容器 (RecyclerView)");
                if (i === 0)
                    return [];
                else
                    break;
            }
            for (const item of resultsContainer.children) {
                const postInfo = extractPostInfoFromNode(item, results.length + 1);
                if (postInfo && !seenTitles.has(postInfo.title)) {
                    seenTitles.add(postInfo.title);
                    results.push(postInfo);
                    if (results.length >= desiredCount)
                        break;
                }
            }
            if (results.length >= desiredCount)
                break;
            if (results.length === lastResultCount) {
                console.log("滚动未产生新结果，停止滚动");
                break;
            }
            lastResultCount = results.length;
            await Tools.UI.swipe(540, 1500, 540, 800);
            await Tools.System.sleep(2000);
        }
        return results;
    }
    async function view_post(params) {
        const { post_title, post_index } = params;
        if (!post_title && !post_index) {
            return createResponse(false, "请提供帖子标题或索引");
        }
        console.log(`尝试查看帖子 - 标题: "${post_title}", 索引: ${post_index}`);
        let targetPost = null;
        let currentIndex = 0;
        const seenTitles = new Set();
        const maxScrolls = 5;
        for (let i = 0; i < maxScrolls; i++) {
            const page = await UINode.getCurrentPage();
            const resultsContainer = page.findByClass("RecyclerView");
            if (!resultsContainer) {
                if (i === 0)
                    return createResponse(false, "未找到帖子列表容器 (RecyclerView)");
                else
                    break;
            }
            for (const item of resultsContainer.children) {
                const postInfo = extractPostInfoFromNode(item, 0); // index doesn't matter here
                if (postInfo && !seenTitles.has(postInfo.title)) {
                    seenTitles.add(postInfo.title);
                    currentIndex++;
                    let shouldClick = false;
                    if (post_index && currentIndex === post_index) {
                        shouldClick = true;
                    }
                    else if (post_title && postInfo.title.includes(post_title)) {
                        shouldClick = true;
                    }
                    if (shouldClick) {
                        targetPost = { title: postInfo.title, item: postInfo.element };
                        break;
                    }
                }
            }
            if (targetPost)
                break;
            await Tools.UI.swipe(540, 1500, 540, 800);
            await Tools.System.sleep(2000);
        }
        if (targetPost) {
            await targetPost.item.click();
            await Tools.System.sleep(3000); // 等待帖子详情页加载
            return createResponse(true, `成功打开帖子: "${targetPost.title}"`, {
                post_title: targetPost.title
            });
        }
        else {
            return createResponse(false, "未找到指定的帖子", { post_title, post_index });
        }
    }
    async function isInPostDetail() {
        const page = await UINode.getCurrentPage();
        // 检测是否在帖子详情页的特征元素
        const likeButton = page.findById("com.xingin.xhs:id/like_button") ||
            page.findByContentDesc("点赞") ||
            page.findByText("赞");
        const commentButton = page.findById("com.xingin.xhs:id/comment_button") ||
            page.findByContentDesc("评论") ||
            page.findByText("评论");
        return !!(likeButton || commentButton);
    }
    async function like_post(params) {
        if (!await isInPostDetail()) {
            return createResponse(false, "当前不在帖子详情页");
        }
        console.log("给帖子点赞");
        const page = await UINode.getCurrentPage();
        const likeButton = page.findById("com.xingin.xhs:id/like_button") ||
            page.findById("com.xingin.xhs:id/iv_like") ||
            page.findByContentDesc("点赞") ||
            page.findByText("赞");
        if (likeButton) {
            await likeButton.click();
            await Tools.System.sleep(1000);
            return createResponse(true, "点赞成功");
        }
        else {
            return createResponse(false, "未找到点赞按钮");
        }
    }
    async function collect_post(params) {
        if (!await isInPostDetail()) {
            return createResponse(false, "当前不在帖子详情页");
        }
        const { collection_name } = params;
        console.log(`收藏帖子到: ${collection_name || "默认收藏夹"}`);
        const page = await UINode.getCurrentPage();
        const collectButton = page.findById("com.xingin.xhs:id/collect_button") ||
            page.findById("com.xingin.xhs:id/iv_collect") ||
            page.findByContentDesc("收藏") ||
            page.findByText("收藏");
        if (collectButton) {
            await collectButton.click();
            await Tools.System.sleep(2000);
            // 如果指定了收藏夹名称，尝试选择
            if (collection_name) {
                const collectionPage = await UINode.getCurrentPage();
                const targetCollection = collectionPage.findByText(collection_name);
                if (targetCollection) {
                    await targetCollection.click();
                    await Tools.System.sleep(1000);
                }
            }
            return createResponse(true, "收藏成功", { collection_name });
        }
        else {
            return createResponse(false, "未找到收藏按钮", { collection_name });
        }
    }
    async function follow_user(params) {
        if (!await isInPostDetail()) {
            return createResponse(false, "当前不在帖子详情页");
        }
        console.log("关注用户");
        const page = await UINode.getCurrentPage();
        const followButton = page.findById("com.xingin.xhs:id/follow_button") ||
            page.findByText("关注") ||
            page.findByText("+ 关注");
        if (followButton) {
            await followButton.click();
            await Tools.System.sleep(1000);
            return createResponse(true, "关注成功");
        }
        else {
            return createResponse(false, "未找到关注按钮或已经关注");
        }
    }
    async function comment_post(params) {
        if (!await isInPostDetail()) {
            return createResponse(false, "当前不在帖子详情页");
        }
        const { comment_text } = params;
        console.log(`发表评论: ${comment_text}`);
        const page = await UINode.getCurrentPage();
        const commentBox = page.findById("com.xingin.xhs:id/comment_input") ||
            page.findById("com.xingin.xhs:id/edit_comment") ||
            page.findByText("说点什么吧...") ||
            page.findByText("写评论...");
        if (commentBox) {
            await commentBox.click();
            await Tools.System.sleep(1000);
            // 输入评论内容
            await Tools.UI.setText(comment_text);
            await Tools.System.sleep(500);
            // 查找发送按钮
            const sendButton = (await UINode.getCurrentPage()).findByText("发送") ||
                (await UINode.getCurrentPage()).findByText("发布") ||
                (await UINode.getCurrentPage()).findById("com.xingin.xhs:id/send_button");
            if (sendButton) {
                await sendButton.click();
                await Tools.System.sleep(2000);
                return createResponse(true, "评论发表成功", { comment_text });
            }
            else {
                return createResponse(false, "未找到发送按钮", { comment_text });
            }
        }
        else {
            return createResponse(false, "未找到评论输入框", { comment_text });
        }
    }
    async function get_post_info(params) {
        if (!await isInPostDetail()) {
            return createResponse(false, "当前不在帖子详情页");
        }
        console.log("获取帖子信息");
        const page = await UINode.getCurrentPage();
        const postInfo = {
            title: "",
            author: "",
            like_count: "",
            comment_count: "",
            collect_count: "",
            content: ""
        };
        // 获取标题和内容
        const titleElements = page.findAllByClass("TextView");
        const mainTextElement = titleElements.find(tv => tv.text && tv.text.length > 10);
        if (mainTextElement) {
            // postInfo.title = mainTextElement.text;
        }
        // 获取作者信息
        const authorElement = page.findById("com.xingin.xhs:id/author_name") ||
            page.findById("com.xingin.xhs:id/user_name");
        if (authorElement) {
            postInfo.author = authorElement.text || "";
        }
        // 获取点赞数
        const likeCountElement = page.findById("com.xingin.xhs:id/like_count");
        if (likeCountElement) {
            postInfo.like_count = likeCountElement.text || "";
        }
        // 获取评论数
        const commentCountElement = page.findById("com.xingin.xhs:id/comment_count");
        if (commentCountElement) {
            postInfo.comment_count = commentCountElement.text || "";
        }
        // 获取收藏数
        const collectCountElement = page.findById("com.xingin.xhs:id/collect_count");
        if (collectCountElement) {
            postInfo.collect_count = collectCountElement.text || "";
        }
        return createResponse(true, "获取帖子信息成功", { post_info: postInfo });
    }
    async function browse_comments(params) {
        if (!await isInPostDetail()) {
            return createResponse(false, "当前不在帖子详情页");
        }
        const { comment_count = 5 } = params;
        console.log(`浏览评论，获取 ${comment_count} 条`);
        // 向下滚动到评论区域
        await Tools.UI.swipe(540, 1500, 540, 800);
        await Tools.System.sleep(2000);
        const page = await UINode.getCurrentPage();
        const comments = [];
        // 查找评论列表
        const commentContainer = page.findById("com.xingin.xhs:id/comment_list") ||
            page.findByClass("RecyclerView");
        if (commentContainer) {
            const commentItems = commentContainer.children;
            for (let i = 0; i < Math.min(commentItems.length, comment_count); i++) {
                const item = commentItems[i];
                const textElements = item.findAllByClass("TextView");
                const authorElement = textElements.find(tv => tv.text && tv.text.length < 20);
                const contentElement = textElements.find(tv => tv.text && tv.text.length > 5 && tv.text !== (authorElement === null || authorElement === void 0 ? void 0 : authorElement.text));
                if (contentElement) {
                    comments.push({
                        author: (authorElement === null || authorElement === void 0 ? void 0 : authorElement.text) || "匿名用户",
                        content: contentElement.text,
                        index: i + 1
                    });
                }
            }
        }
        return createResponse(true, `获取到 ${comments.length} 条评论`, {
            comments: comments,
            comment_count: comments.length
        });
    }
    async function publish_post(params) {
        const { content_text, image_paths, tags, location } = params;
        console.log(`发布帖子: ${content_text.substring(0, 20)}...`);
        // 确保小红书已在前台运行
        if (!await ensureMain()) {
            return createResponse(false, "无法启动或切换到小红书");
        }
        // 点击发布按钮（通常在底部导航栏中间）
        const page = await UINode.getCurrentPage();
        const publishButton = page.findById("com.xingin.xhs:id/tab_publish") ||
            page.findByText("发布") ||
            page.findByText("+") ||
            page.findByContentDesc("发布");
        if (!publishButton) {
            return createResponse(false, "未找到发布按钮");
        }
        await publishButton.click();
        await Tools.System.sleep(3000);
        // 选择发布类型（如果需要）
        const noteOption = (await UINode.getCurrentPage()).findByText("笔记") ||
            (await UINode.getCurrentPage()).findByText("图文");
        if (noteOption) {
            await noteOption.click();
            await Tools.System.sleep(2000);
        }
        // 如果提供了图片路径，添加图片
        if (image_paths) {
            const imagePaths = image_paths.split(',');
            for (const imagePath of imagePaths) {
                // 这里需要根据实际的图片选择界面来实现
                console.log(`添加图片: ${imagePath.trim()}`);
                // 实际实现中需要打开相册、选择图片等步骤
            }
        }
        // 输入文字内容
        const textInput = (await UINode.getCurrentPage()).findById("com.xingin.xhs:id/content_input") ||
            (await UINode.getCurrentPage()).findById("com.xingin.xhs:id/edit_content") ||
            (await UINode.getCurrentPage()).findByText("分享你的生活...");
        if (textInput) {
            await textInput.click();
            await Tools.System.sleep(1000);
            await Tools.UI.setText(content_text);
            await Tools.System.sleep(1000);
        }
        // 添加标签
        if (tags) {
            const tagList = tags.split(',');
            for (const tag of tagList) {
                const tagText = tag.trim();
                if (tagText) {
                    // 在文本末尾添加话题标签
                    await Tools.UI.setText(` #${tagText}`);
                    await Tools.System.sleep(500);
                }
            }
        }
        // 添加位置信息
        if (location) {
            const locationButton = (await UINode.getCurrentPage()).findByText("添加地点") ||
                (await UINode.getCurrentPage()).findById("com.xingin.xhs:id/location_button");
            if (locationButton) {
                await locationButton.click();
                await Tools.System.sleep(2000);
                // 搜索并选择位置
                await Tools.UI.setText(location);
                await Tools.System.sleep(1000);
                const firstLocation = (await UINode.getCurrentPage()).findByClass("TextView");
                if (firstLocation && firstLocation.text && firstLocation.text.includes(location)) {
                    await firstLocation.click();
                    await Tools.System.sleep(1000);
                }
            }
        }
        // 发布帖子
        const finalPublishButton = (await UINode.getCurrentPage()).findByText("发布") ||
            (await UINode.getCurrentPage()).findById("com.xingin.xhs:id/publish_button");
        if (finalPublishButton) {
            await finalPublishButton.click();
            await Tools.System.sleep(5000); // 等待发布完成
            return createResponse(true, "帖子发布成功", {
                content_text: content_text,
                image_paths: image_paths,
                tags: tags,
                location: location
            });
        }
        else {
            return createResponse(false, "未找到最终发布按钮", {
                content_text: content_text
            });
        }
    }
    async function navigate_to_home(params) {
        console.log("导航到首页");
        if (await ensureMain()) {
            const page = await UINode.getCurrentPage();
            const homeTab = page.findByText("首页") || page.findById("com.xingin.xhs:id/tab_home");
            if (homeTab) {
                await homeTab.click();
                await Tools.System.sleep(1000);
            }
            return createResponse(true, "已导航到首页");
        }
        else {
            return createResponse(false, "无法导航到首页");
        }
    }
    async function navigate_to_profile(params) {
        console.log("导航到个人主页");
        if (await ensureMain()) {
            const page = await UINode.getCurrentPage();
            const profileTab = page.findByText("我") ||
                page.findById("com.xingin.xhs:id/tab_profile") ||
                page.findByText("个人主页");
            if (profileTab) {
                await profileTab.click();
                await Tools.System.sleep(2000);
                return createResponse(true, "已导航到个人主页");
            }
            else {
                return createResponse(false, "未找到个人主页标签");
            }
        }
        else {
            return createResponse(false, "无法导航到个人主页");
        }
    }
    async function navigate_to_search(params) {
        console.log("导航到搜索页面");
        if (await ensureMain()) {
            const page = await UINode.getCurrentPage();
            const searchTab = page.findByText("搜索") ||
                page.findById("com.xingin.xhs:id/tab_search") ||
                page.findByContentDesc("搜索");
            if (searchTab) {
                await searchTab.click();
                await Tools.System.sleep(1500);
                return createResponse(true, "已导航到搜索页面");
            }
            else {
                // 尝试点击顶部搜索区域
                await Tools.UI.tap(540, 200);
                await Tools.System.sleep(1500);
                return createResponse(true, "已打开搜索页面");
            }
        }
        else {
            return createResponse(false, "无法导航到搜索页面");
        }
    }
    async function navigate_to_publish(params) {
        console.log("导航到发布页面");
        if (await ensureMain()) {
            const page = await UINode.getCurrentPage();
            const publishTab = page.findById("com.xingin.xhs:id/tab_publish") ||
                page.findByText("发布") ||
                page.findByText("+");
            if (publishTab) {
                await publishTab.click();
                await Tools.System.sleep(2000);
                return createResponse(true, "已导航到发布页面");
            }
            else {
                return createResponse(false, "未找到发布按钮");
            }
        }
        else {
            return createResponse(false, "无法导航到发布页面");
        }
    }
    async function back_to_feed(params) {
        console.log("从帖子详情页返回到信息流");
        if (await isInPostDetail()) {
            await Tools.UI.pressKey("KEYCODE_BACK");
            await Tools.System.sleep(2000);
            return createResponse(true, "已返回到信息流列表");
        }
        else {
            return createResponse(true, "当前不在帖子详情页，无需返回");
        }
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params);
            complete(Object.assign({}, result));
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }
    async function main(params) {
        console.log("=== 小红书智能助手测试 ===");
        console.log("测试搜索功能...");
        let res = await search_content({ keyword: "美食" });
        console.log(JSON.stringify(res, null, 2));
    }
    // 导出所有工具
    return {
        browse_home_feed: async (params) => await wrapToolExecution(browse_home_feed, params),
        search_content: async (params) => await wrapToolExecution(search_content, params),
        view_post: async (params) => await wrapToolExecution(view_post, params),
        like_post: async (params) => await wrapToolExecution(like_post, params),
        collect_post: async (params) => await wrapToolExecution(collect_post, params),
        follow_user: async (params) => await wrapToolExecution(follow_user, params),
        comment_post: async (params) => await wrapToolExecution(comment_post, params),
        get_post_info: async (params) => await wrapToolExecution(get_post_info, params),
        browse_comments: async (params) => await wrapToolExecution(browse_comments, params),
        publish_post: async (params) => await wrapToolExecution(publish_post, params),
        navigate_to_home: async (params) => await wrapToolExecution(navigate_to_home, params),
        navigate_to_profile: async (params) => await wrapToolExecution(navigate_to_profile, params),
        navigate_to_search: async (params) => await wrapToolExecution(navigate_to_search, params),
        navigate_to_publish: async (params) => await wrapToolExecution(navigate_to_publish, params),
        back_to_feed: async (params) => await wrapToolExecution(back_to_feed, params),
        main: async (params) => await wrapToolExecution(main, params),
    };
})();
// 逐个导出，以便在外部调用
exports.browse_home_feed = XiaohongshuAssistant.browse_home_feed;
exports.search_content = XiaohongshuAssistant.search_content;
exports.view_post = XiaohongshuAssistant.view_post;
exports.like_post = XiaohongshuAssistant.like_post;
exports.collect_post = XiaohongshuAssistant.collect_post;
exports.follow_user = XiaohongshuAssistant.follow_user;
exports.comment_post = XiaohongshuAssistant.comment_post;
exports.get_post_info = XiaohongshuAssistant.get_post_info;
exports.browse_comments = XiaohongshuAssistant.browse_comments;
exports.publish_post = XiaohongshuAssistant.publish_post;
exports.navigate_to_home = XiaohongshuAssistant.navigate_to_home;
exports.navigate_to_profile = XiaohongshuAssistant.navigate_to_profile;
exports.navigate_to_search = XiaohongshuAssistant.navigate_to_search;
exports.navigate_to_publish = XiaohongshuAssistant.navigate_to_publish;
exports.back_to_feed = XiaohongshuAssistant.back_to_feed;
exports.main = XiaohongshuAssistant.main;
