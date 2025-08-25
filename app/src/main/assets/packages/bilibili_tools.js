/* METADATA
{
    "name": "bilibili_tools",
    "description": "提供B站视频信息分析功能，包括获取字幕、弹幕、评论和搜索视频。",
    "tools": [
        {
            "name": "get_subtitles",
            "description": "从Bilibili视频中获取字幕。",
            "parameters": [
                { "name": "url", "description": "Bilibili视频URL，例如：https://www.bilibili.com/video/BV1x341177NN", "type": "string", "required": true }
            ]
        },
        {
            "name": "get_danmaku",
            "description": "从Bilibili视频中获取弹幕。",
            "parameters": [
                { "name": "url", "description": "Bilibili视频URL，例如：https://www.bilibili.com/video/BV1x341177NN", "type": "string", "required": true }
            ]
        },
        {
            "name": "get_comments",
            "description": "从Bilibili视频中获取热门评论。",
            "parameters": [
                { "name": "url", "description": "Bilibili视频URL，例如：https://www.bilibili.com/video/BV1x341177NN", "type": "string", "required": true }
            ]
        },
        {
            "name": "search_videos",
            "description": "在Bilibili上搜索视频。",
            "parameters": [
                { "name": "keyword", "description": "要搜索的关键词", "type": "string", "required": true },
                { "name": "page", "description": "页码，默认为1", "type": "number", "required": false },
                { "name": "count", "description": "返回结果的数量，默认10，最多20", "type": "number", "required": false }
            ]
        }
    ],
    "category": "NETWORK"
}
*/
//参考了https://github.com/vruses/bili-api-interceptor/blob/master/dist/bili-api-interceptor.user.js 特此感谢
const BilibiliVideoAnalysis = (function () {
    // Bilibili API endpoints
    const API_GET_VIEW_INFO = "https://api.bilibili.com/x/web-interface/view";
    const API_GET_SUBTITLE_LIST = "https://api.bilibili.com/x/player/v2";
    const API_GET_DANMAKU = "https://api.bilibili.com/x/v1/dm/list.so";
    const API_GET_COMMENTS = "https://api.bilibili.com/x/v2/reply/wbi/main";
    const API_SEARCH = "https://api.bilibili.com/x/web-interface/search/all/v2";
    const API_NAV = "https://api.bilibili.com/x/web-interface/nav";
    const client = OkHttp.newClient();
    let SESSDATA;
    let wbiKeys = null;
    async function init() {
        if (SESSDATA === undefined) {
            // SESSDATA 是B站的Cookie，用于访问一些需要登录的API。
            // 为了简单起见，这里我们不作强制要求。
            // 如果遇到请求失败的情况，可以尝试手动在这里设置你的SESSDATA值。
            // 例如: SESSDATA = "your_sessdata_cookie_value";
            SESSDATA = "";
            if (!SESSDATA) {
                console.log("SESSDATA 未设置，部分需要登录的请求可能会失败。");
            }
        }
    }
    function getHeaders() {
        const headers = {
            'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36',
            'Referer': 'https://www.bilibili.com/'
        };
        if (SESSDATA) {
            headers['Cookie'] = `SESSDATA=${SESSDATA}`;
        }
        return headers;
    }
    function unescapeXml(text) {
        return text.replace(/&lt;/g, '<')
            .replace(/&gt;/g, '>')
            .replace(/&amp;/g, '&')
            .replace(/&quot;/g, '"')
            .replace(/&apos;/g, "'");
    }
    const mixinKeyEncTab = [
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    ];
    function getMixinKey(orig) {
        return mixinKeyEncTab.map(n => orig[n]).join('').slice(0, 32);
    }
    function encWbi(params, img_key, sub_key) {
        const mixin_key = getMixinKey(img_key + sub_key);
        const curr_time = Math.round(Date.now() / 1000);
        const chr_filter = /[!'()*]/g;
        const new_params = Object.assign(Object.assign({}, params), { wts: curr_time });
        const query = Object.keys(new_params).sort().map(key => {
            const value = String(new_params[key]).replace(chr_filter, "");
            return `${encodeURIComponent(key)}=${encodeURIComponent(value)}`;
        }).join('&');
        const w_rid = CryptoJS.MD5(query + mixin_key).toString();
        return `${query}&w_rid=${w_rid}`;
    }
    async function get_wbi_keys() {
        var _a, _b, _c, _d;
        if (wbiKeys) {
            return wbiKeys;
        }
        try {
            const response = await client.get(API_NAV, getHeaders());
            if (!response.isSuccessful()) {
                console.error("Failed to get WBI keys, status: " + response.statusCode);
                return null;
            }
            const navData = response.json();
            // The API may return a non-zero code for non-logged-in users, but still provide the WBI keys.
            if (!((_b = (_a = navData.data) === null || _a === void 0 ? void 0 : _a.wbi_img) === null || _b === void 0 ? void 0 : _b.img_url) || !((_d = (_c = navData.data) === null || _c === void 0 ? void 0 : _c.wbi_img) === null || _d === void 0 ? void 0 : _d.sub_url)) {
                console.error("Failed to get WBI keys: " + (navData.message || "No wbi_img in response"));
                return null;
            }
            const imgUrl = navData.data.wbi_img.img_url;
            const subUrl = navData.data.wbi_img.sub_url;
            const img_key = imgUrl.substring(imgUrl.lastIndexOf('/') + 1, imgUrl.lastIndexOf('.'));
            const sub_key = subUrl.substring(subUrl.lastIndexOf('/') + 1, subUrl.lastIndexOf('.'));
            wbiKeys = { img_key, sub_key };
            return wbiKeys;
        }
        catch (e) {
            console.error(`Error fetching WBI keys: ${e.message}`);
            return null;
        }
    }
    async function extract_bvid(url) {
        let match = url.match(/BV[a-zA-Z0-9_]+/);
        if (match) {
            return match[0];
        }
        if (url.includes('b23.tv')) {
            try {
                const request = client.newRequest().url(url).method("HEAD").build();
                const response = await request.execute();
                const finalUrl = response.raw.url;
                if (finalUrl) {
                    match = finalUrl.match(/BV[a-zA-Z0-9_]+/);
                    if (match) {
                        return match[0];
                    }
                }
            }
            catch (e) {
                console.error(`Error resolving short URL: ${e}`);
            }
        }
        return null;
    }
    async function get_video_basic_info(bvid) {
        try {
            const url = `${API_GET_VIEW_INFO}?bvid=${bvid}`;
            const response = await client.get(url, getHeaders());
            if (!response.isSuccessful()) {
                return { aid: null, cid: null, error: `Failed to get video info, status: ${response.statusCode}` };
            }
            const data = response.json();
            if (data.code !== 0) {
                return { aid: null, cid: null, error: `Failed to get video info: ${data.message}` };
            }
            const videoData = data.data;
            return { aid: videoData.aid, cid: videoData.cid, error: null };
        }
        catch (e) {
            return { aid: null, cid: null, error: `Failed to fetch video details: ${e.message}` };
        }
    }
    async function get_subtitles_from_api(aid, cid) {
        var _a, _b;
        const subtitles = [];
        try {
            const url = `${API_GET_SUBTITLE_LIST}?aid=${aid}&cid=${cid}`;
            const response = await client.get(url, getHeaders());
            if (!response.isSuccessful()) {
                return { subtitles: [], error: `Could not fetch subtitles list, status: ${response.statusCode}` };
            }
            const subtitleListData = response.json();
            if (subtitleListData.code === 0 && ((_b = (_a = subtitleListData.data) === null || _a === void 0 ? void 0 : _a.subtitle) === null || _b === void 0 ? void 0 : _b.subtitles)) {
                for (const sub_meta of subtitleListData.data.subtitle.subtitles) {
                    if (sub_meta.subtitle_url) {
                        try {
                            const subtitle_json_url = `https:${sub_meta.subtitle_url}`;
                            const sub_content_response = await client.get(subtitle_json_url, getHeaders());
                            if (sub_content_response.isSuccessful()) {
                                const sub_content = await sub_content_response.json();
                                const subtitle_body = sub_content.body || [];
                                const content_list = subtitle_body.map((item) => item.content || '');
                                subtitles.push({
                                    lan: sub_meta.lan,
                                    content: content_list
                                });
                            }
                        }
                        catch (e) {
                            console.error(`Could not fetch or parse subtitle content from ${sub_meta.subtitle_url}: ${e.message}`);
                        }
                    }
                }
            }
            return { subtitles, error: null };
        }
        catch (e) {
            return { subtitles: [], error: `Could not fetch subtitles: ${e.message}` };
        }
    }
    async function get_danmaku_from_api(cid) {
        const danmaku_list = [];
        try {
            const url = `${API_GET_DANMAKU}?oid=${cid}`;
            const response = await client.get(url, getHeaders());
            if (!response.isSuccessful()) {
                return { danmaku: [], error: `Failed to get danmaku, status: ${response.statusCode}` };
            }
            const compressedData = await response.bodyAsBase64();
            console.log("base64" + compressedData);
            if (!compressedData) {
                return { danmaku: [], error: null };
            }
            const danmaku_content = pako.inflate(compressedData, { to: 'string' });
            const regex = /<d p=".*?">(.*?)<\/d>/g;
            let match;
            while ((match = regex.exec(danmaku_content)) !== null) {
                danmaku_list.push(unescapeXml(match[1]));
            }
            return { danmaku: danmaku_list, error: null };
        }
        catch (e) {
            return { danmaku: [], error: `Failed to get or parse danmaku: ${e.message}` };
        }
    }
    async function get_comments_from_api(aid) {
        var _a, _b;
        const all_comments = [];
        const keys = await get_wbi_keys();
        if (!keys) {
            return { comments: "", count: 0, error: "获取 WBI keys 失败，无法请求评论 API" };
        }
        let page_num = 1;
        let total_pages = 1;
        try {
            while (page_num <= total_pages) {
                const params = {
                    type: 1,
                    oid: aid,
                    sort: 2, // sort=2 for hot
                    pn: page_num
                };
                const signed_query = encWbi(params, keys.img_key, keys.sub_key);
                const url = `${API_GET_COMMENTS}?${signed_query}`;
                const response = await client.get(url, getHeaders());
                if (!response.isSuccessful()) {
                    console.error(`Failed to get comments page ${page_num}, status: ${response.statusCode}`);
                    page_num++;
                    continue;
                }
                const comments_data = response.json();
                if (comments_data.code !== 0) {
                    console.error(`API error on page ${page_num}: ${comments_data.message}`);
                    page_num++;
                    continue;
                }
                if (page_num === 1 && ((_a = comments_data.data) === null || _a === void 0 ? void 0 : _a.page)) {
                    total_pages = Math.ceil(comments_data.data.page.count / comments_data.data.page.size);
                }
                if ((_b = comments_data.data) === null || _b === void 0 ? void 0 : _b.replies) {
                    for (const comment of comments_data.data.replies) {
                        const formatted_comment = format_comment(comment);
                        if (formatted_comment) {
                            all_comments.push(formatted_comment);
                        }
                    }
                }
                page_num++;
            }
            const comment_summary = format_comments_to_string(all_comments);
            return { comments: comment_summary, count: all_comments.length, error: null };
        }
        catch (e) {
            const comment_summary = format_comments_to_string(all_comments);
            return { comments: comment_summary, count: all_comments.length, error: `Failed to get comments: ${e.message}` }; // 返回已获取的部分数据
        }
    }
    function format_comment(comment_item) {
        const sub_comments = (comment_item.replies || []).map(format_comment).filter(Boolean);
        return {
            user: comment_item.member.uname,
            content: comment_item.content.message,
            likes: comment_item.like || 0,
            time: new Date(comment_item.ctime * 1000).toLocaleDateString(),
            sub_comments: sub_comments
        };
    }
    function format_comments_to_string(comments) {
        const result = [];
        function format_single(comment, indent) {
            result.push(`${indent}- ${comment.user} (👍${comment.likes}) [${comment.time}]: ${comment.content}`);
            if (comment.sub_comments && comment.sub_comments.length > 0) {
                for (const sub of comment.sub_comments) {
                    format_single(sub, indent + "  ");
                }
            }
        }
        for (const comment of comments) {
            format_single(comment, "");
        }
        return result.join('\n');
    }
    async function get_subtitles(params) {
        await init();
        const bvid = await extract_bvid(params.url);
        if (!bvid) {
            return { success: false, message: `错误: 无法从 URL 提取 BV 号: ${params.url}` };
        }
        const { aid, cid, error: infoError } = await get_video_basic_info(bvid);
        if (infoError) {
            return { success: false, message: `获取视频信息失败: ${infoError}` };
        }
        if (!aid || !cid) {
            return { success: false, message: '获取视频 aid 或 cid 失败' };
        }
        const { subtitles, error: subtitlesError } = await get_subtitles_from_api(aid, cid);
        if (subtitlesError) {
            return { success: false, message: `获取字幕失败: ${subtitlesError}` };
        }
        if (subtitles.length === 0) {
            return { success: true, message: "该视频没有字幕", data: [] };
        }
        return { success: true, message: `成功获取 ${subtitles.length} 门语言的字幕`, data: subtitles };
    }
    async function get_danmaku(params) {
        await init();
        const bvid = await extract_bvid(params.url);
        if (!bvid) {
            return { success: false, message: `错误: 无法从 URL 提取 BV 号: ${params.url}` };
        }
        const { aid, cid, error: infoError } = await get_video_basic_info(bvid);
        if (infoError) {
            return { success: false, message: `获取视频信息失败: ${infoError}` };
        }
        if (!cid) {
            return { success: false, message: '获取视频 cid 失败' };
        }
        const { danmaku, error: danmakuError } = await get_danmaku_from_api(cid);
        if (danmakuError) {
            return { success: false, message: `获取弹幕失败: ${danmakuError}` };
        }
        if (danmaku.length === 0) {
            return { success: true, message: "该视频没有弹幕", data: [] };
        }
        return { success: true, message: `成功获取 ${danmaku.length} 条弹幕`, data: danmaku };
    }
    async function get_comments(params) {
        await init();
        const bvid = await extract_bvid(params.url);
        if (!bvid) {
            return { success: false, message: `错误: 无法从 URL 提取 BV 号: ${params.url}` };
        }
        const { aid, error: infoError } = await get_video_basic_info(bvid);
        if (infoError) {
            return { success: false, message: `获取视频信息失败: ${infoError}` };
        }
        if (!aid) {
            return { success: false, message: '获取视频 aid 失败' };
        }
        const { comments, count, error: commentsError } = await get_comments_from_api(aid);
        if (commentsError && count === 0) {
            return { success: false, message: `获取评论失败: ${commentsError}` };
        }
        if (count === 0) {
            return { success: true, message: "该视频没有热门评论", data: "" };
        }
        const message = commentsError
            ? `成功获取 ${count} 条热门评论，但过程中发生错误: ${commentsError}`
            : `成功获取 ${count} 条热门评论`;
        return { success: true, message: message, data: comments };
    }
    async function search_videos_from_api(keyword, page) {
        const keys = await get_wbi_keys();
        if (!keys) {
            return { results: null, error: "获取 WBI keys 失败，无法请求搜索 API" };
        }
        try {
            const params = {
                keyword: keyword,
                page: page,
                search_type: "video"
            };
            const signed_query = encWbi(params, keys.img_key, keys.sub_key);
            const url = `${API_SEARCH}?${signed_query}`;
            const response = await client.get(url, getHeaders());
            if (!response.isSuccessful()) {
                return { results: null, error: `搜索失败, status: ${response.statusCode}` };
            }
            const search_data = response.json();
            if (search_data.code !== 0) {
                return { results: null, error: `搜索 API 错误: ${search_data.message}` };
            }
            return { results: search_data.data, error: null };
        }
        catch (e) {
            return { results: null, error: `搜索时发生异常: ${e.message}` };
        }
    }
    function format_search_results_to_string(data, count) {
        var _a;
        if (!data.result) {
            return { formatted: "没有找到相关结果。", result_count: 0, total_count: 0 };
        }
        const videoResults = (_a = data.result
            .find((item) => item.result_type === "video")) === null || _a === void 0 ? void 0 : _a.data;
        if (!videoResults || videoResults.length === 0) {
            return { formatted: "没有找到相关视频结果。", result_count: 0, total_count: data.numResults || 0 };
        }
        const slicedResults = videoResults.slice(0, count);
        const formattedResults = slicedResults
            .map((video, index) => {
                var _a, _b, _c;
                const cleanTitle = video.title.replace(/<em class="keyword">|<\/em>/g, "");
                const cleanDescription = video.description.replace(/<em class="keyword">|<\/em>/g, "");
                return [
                    `${index + 1}. "${cleanTitle}" - ${video.author}`,
                    `   BV ID: ${video.bvid}`,
                    `   播放: ${(_a = video.play) === null || _a === void 0 ? void 0 : _a.toLocaleString()}`,
                    `   弹幕: ${(_b = video.danmaku) === null || _b === void 0 ? void 0 : _b.toLocaleString()}`,
                    `   点赞: ${(_c = video.like) === null || _c === void 0 ? void 0 : _c.toLocaleString()}`,
                    `   时长: ${video.duration}`,
                    `   发布于: ${new Date(video.pubdate * 1000).toLocaleDateString()}`,
                    `   简介: ${cleanDescription === null || cleanDescription === void 0 ? void 0 : cleanDescription.substring(0, 100)}${(cleanDescription === null || cleanDescription === void 0 ? void 0 : cleanDescription.length) > 100 ? "..." : ""}`,
                ].join("\n");
            })
            .join("\n\n");
        return { formatted: formattedResults, result_count: slicedResults.length, total_count: data.numResults || 0 };
    }
    async function search_videos(params) {
        await init();
        const { keyword, page = 1, count = 10 } = params;
        if (count > 20) {
            return { success: false, message: "参数 'count' 不能超过 20" };
        }
        const { results, error } = await search_videos_from_api(keyword, page);
        if (error) {
            return { success: false, message: `搜索失败: ${error}` };
        }
        const { formatted, result_count, total_count } = format_search_results_to_string(results, count);
        if (result_count === 0) {
            return { success: true, message: "没有找到相关视频。", data: "" };
        }
        const message = `成功为 "${keyword}" 找到 ${total_count} 个相关视频，当前显示第 ${page} 页的 ${result_count} 个结果。`;
        return { success: true, message, data: formatted };
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params);
            complete(result);
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }
    async function main() {
        console.log("--- Bilibili Video Analysis Tool Test ---");
        const testUrl = "https://www.bilibili.com/video/BV1NRE5zmE3W";
        const testKeyword = "原神";
        console.log("\n[1/4] Testing get_subtitles...");
        const subtitlesResult = await get_subtitles({ url: testUrl });
        console.log(JSON.stringify(subtitlesResult, null, 2));
        console.log("\n[2/4] Testing get_danmaku...");
        const danmakuResult = await get_danmaku({ url: testUrl });
        // Print first 5 danmaku
        if (danmakuResult.success && Array.isArray(danmakuResult.data)) {
            danmakuResult.data = danmakuResult.data.slice(0, 5);
        }
        console.log(JSON.stringify(danmakuResult, null, 2));
        console.log("\n[3/4] Testing get_comments...");
        const commentsResult = await get_comments({ url: testUrl });
        // For compact string format, we don't need JSON.stringify and can print directly
        if (commentsResult.success) {
            console.log(`Success: ${commentsResult.message}`);
            console.log("---------- Comments ----------");
            console.log(commentsResult.data);
            console.log("----------------------------");
        }
        else {
            console.error(`Failure: ${commentsResult.message}`);
        }
        console.log("\n[4/4] Testing search_videos...");
        const searchResult = await search_videos({ keyword: testKeyword, count: 10 });
        if (searchResult.success) {
            console.log(`Success: ${searchResult.message}`);
            console.log(`---------- Search Results for "${testKeyword}" ----------`);
            console.log(searchResult.data);
            console.log("------------------------------------------");
        }
        else {
            console.error(`Failure: ${searchResult.message}`);
        }
        complete({ success: true, message: "Test finished." });
    }
    return {
        get_subtitles: (params) => wrapToolExecution(get_subtitles, params),
        get_danmaku: (params) => wrapToolExecution(get_danmaku, params),
        get_comments: (params) => wrapToolExecution(get_comments, params),
        search_videos: (params) => wrapToolExecution(search_videos, params),
        main,
    };
})();
exports.get_subtitles = BilibiliVideoAnalysis.get_subtitles;
exports.get_danmaku = BilibiliVideoAnalysis.get_danmaku;
exports.get_comments = BilibiliVideoAnalysis.get_comments;
exports.search_videos = BilibiliVideoAnalysis.search_videos;
exports.main = BilibiliVideoAnalysis.main;
