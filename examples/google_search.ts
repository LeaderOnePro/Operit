/* METADATA
{
    "name": "google_search",
    "description": "提供 Google 普通搜索与 Google Scholar 学术搜索能力，支持设置语言与返回条数。",
    "enabledByDefault": false,
    "tools": [
        {
            "name": "search_web",
            "description": "执行 Google 普通搜索，返回网页搜索结果。",
            "parameters": [
                { "name": "query", "description": "搜索关键词", "type": "string", "required": true },
                { "name": "max_results", "description": "返回结果数量，默认 10，最大 20", "type": "number", "required": false },
                { "name": "language", "description": "界面语言参数，默认 en", "type": "string", "required": false },
                { "name": "region", "description": "地区参数，例如 us、cn。默认 us", "type": "string", "required": false }
            ]
        },
        {
            "name": "search_scholar",
            "description": "执行 Google Scholar 学术搜索，返回学术文献结果。",
            "parameters": [
                { "name": "query", "description": "搜索关键词", "type": "string", "required": true },
                { "name": "max_results", "description": "返回结果数量，默认 10，最大 20", "type": "number", "required": false },
                { "name": "language", "description": "界面语言参数，默认 en", "type": "string", "required": false }
            ]
        }
    ],
    "category": "NETWORK"
}
*/

const googleSearch = (function () {
    type SearchParams = {
        query: string;
        max_results?: number;
        language?: string;
        region?: string;
    };

    type ScholarSearchParams = {
        query: string;
        max_results?: number;
        language?: string;
    };

    type GoogleSearchResult = {
        title: string;
        url: string;
        snippet: string;
        position: number;
    };

    const GOOGLE_SEARCH_URL = "https://www.google.com/search";
    const GOOGLE_SCHOLAR_URL = "https://scholar.google.com/scholar";
    const MAX_RESULTS = 20;

    function buildUrl(base: string, params: Record<string, string>): string {
        const queryString = Object.entries(params)
            .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
            .join("&");
        return `${base}?${queryString}`;
    }

    async function fetchHtmlViaWebVisit(url: string): Promise<string> {
        const result = await Tools.Net.visit(url);
        // VisitWebResultData may include content and final URL
        return typeof result === "string" ? result : (result?.content ?? "");
    }

    // 解析相关逻辑已移除，直接返回 visit 的纯文本结果

    async function searchWeb(params: SearchParams): Promise<string> {
        if (!params.query || params.query.trim() === "") {
            throw new Error("请提供有效的 query 参数。");
        }
        const maxResults = Math.min(Math.max(params.max_results || 10, 1), MAX_RESULTS);
        const language = params.language || "en";
        const region = params.region || "us";
        const url = buildUrl(GOOGLE_SEARCH_URL, {
            q: params.query,
            hl: language,
            gl: region,
            num: String(maxResults),
            pws: "0",
            // 避免显式设置 tbm/source 触发挑战页
        });
        // 直接返回文本
        return await fetchHtmlViaWebVisit(url);
    }

    async function searchScholar(params: ScholarSearchParams): Promise<string> {
        if (!params.query || params.query.trim() === "") {
            throw new Error("请提供有效的 query 参数。");
        }
        const maxResults = Math.min(Math.max(params.max_results || 10, 1), MAX_RESULTS);
        const language = params.language || "en";
        const url = buildUrl(GOOGLE_SCHOLAR_URL, {
            q: params.query,
            hl: language,
            as_sdt: "0,5",
            num: String(maxResults)
        });
        // 直接返回文本
        return await fetchHtmlViaWebVisit(url);
    }

    async function wrapToolExecution<P>(
        func: (params: P) => Promise<string>,
        params: P,
        successMessage: string,
        failMessage: string
    ) {
        try {
            const data = await func(params);
            complete({
                success: true,
                message: successMessage,
                data
            });
        } catch (error: any) {
            console.error(`Tool ${func.name} failed:`, error);
            complete({
                success: false,
                message: `${failMessage}: ${error.message}`,
                error_stack: error.stack
            });
        }
    }

    async function main(): Promise<string> {
        const web = await searchWeb({
            query: "test",
            max_results: 1,
            language: "en",
            region: "us"
        });
        const scholar = await searchScholar({
            query: "test",
            max_results: 1,
            language: "en"
        });
        return `Web:\n${web}\n\nScholar:\n${scholar}`;
    }

    function runMainTool(_: {} = {}): Promise<string> {
        return main();
    }

    return {
        search_web: (params: SearchParams) =>
            wrapToolExecution(searchWeb, params, "Google 搜索成功", "Google 搜索失败"),
        search_scholar: (params: ScholarSearchParams) =>
            wrapToolExecution(searchScholar, params, "Google Scholar 搜索成功", "Google Scholar 搜索失败"),
        main: () => wrapToolExecution(runMainTool, {}, "测试成功", "测试失败")
    };
})();

exports.search_web = googleSearch.search_web;
exports.search_scholar = googleSearch.search_scholar;
exports.main = googleSearch.main;

