/* METADATA
{
  name: various_search
  description: 提供多平台搜索功能，支持从必应、百度、搜狗、夸克等平台获取搜索结果。
  enabledByDefault: true
  
  tools: [
    {
      name: search_bing
      description: 使用必应搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        }
      ]
    },
    {
      name: search_baidu
      description: 使用百度搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        }
      ]
    },
    {
      name: search_sogou
      description: 使用搜狗搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        }
      ]
    },
    {
      name: search_quark
      description: 使用夸克搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        }
      ]
    },
    {
      name: combined_search
      description: 在多个平台同时执行搜索。建议用户要求搜索的时候默认使用这个工具。
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: platforms
          description: 搜索平台列表字符串，可选值包括"bing","baidu","sogou","quark"，多个平台用逗号分隔，比如"bing,baidu,sogou,quark"
          type: string
          required: true
        }
      ]
    }
  ]
}
*/

const various_search = (function () {
  async function performSearch(platform: string, url: string, query: string, page?: number) {
    try {
      const response = await Tools.Net.visit(url);
      if (!response) {
        throw new Error(`无法获取 ${platform} 搜索结果`);
      }
      const result: any = {
        platform,
        success: true,
        query: query,
        page: page,
        content: response.content,
        visitKey: response.visitKey,
        links: response.links || []
      };
      return result;
    } catch (error: any) {
      return {
        platform,
        success: false,
        message: `${platform} 搜索失败: ${error.message}`
      };
    }
  }

  async function search_bing(params: { query: string }) {
    const { query } = params;
    const encodedQuery = encodeURIComponent(query);
    const url = `https://cn.bing.com/search?q=${encodedQuery}&FORM=HDRSC1`;
    return performSearch('bing', url, query);
  }

  async function search_baidu(params: { query: string, page?: string }) {
    const { query } = params;
    let page = 1;
    if (params.page) {
      page = parseInt(params.page, 10);
    }
    const pn = (page - 1) * 10;
    const encodedQuery = encodeURIComponent(query);
    const url = `https://www.baidu.com/s?wd=${encodedQuery}&pn=${pn}`;
    return performSearch('baidu', url, query, page);
  }

  async function search_sogou(params: { query: string, page?: string }) {
    const { query } = params;
    let page = 1;
    if (params.page) {
      page = parseInt(params.page, 10);
    }
    const encodedQuery = encodeURIComponent(query);
    const url = `https://www.sogou.com/web?query=${encodedQuery}&page=${page}`;
    return performSearch('sogou', url, query, page);
  }

  async function search_quark(params: { query: string, page?: string }) {
    const { query } = params;
    let page = 1;
    if (params.page) {
      page = parseInt(params.page, 10);
    }
    const encodedQuery = encodeURIComponent(query);
    const url = `https://quark.sm.cn/s?q=${encodedQuery}&page=${page}`;
    return performSearch('quark', url, query, page);
  }

  const searchFunctions: any = {
    bing: search_bing,
    baidu: search_baidu,
    sogou: search_sogou,
    quark: search_quark
  };

  async function combined_search(params: { query: string, platforms: string }) {
    const { query, platforms } = params;
    const platformKeysRaw = platforms.split(',');
    const platformKeys: string[] = [];
    for (const platform of platformKeysRaw) {
      const trimmedPlatform = platform.trim();
      if (trimmedPlatform) {
        platformKeys.push(trimmedPlatform);
      }
    }

    const searchPromises: Promise<any>[] = [];
    for (const platform of platformKeys) {
      const searchFn = searchFunctions[platform];
      if (searchFn) {
        searchPromises.push(searchFn({ query, page: '1' }));
      } else {
        searchPromises.push(Promise.resolve({ platform, success: false, message: `不支持的搜索平台: ${platform}` }));
      }
    }

    return Promise.all(searchPromises);
  }

  async function main() {
    const result = await combined_search({ query: '如何学习编程', platforms: 'bing,baidu,sogou,quark' });
    console.log(result);
  }

  function wrap(coreFunction: (params: any) => Promise<any>) {
    return async (params: any) => {
      const result = await coreFunction(params);

      const formatOne = (item: any) => {
        if (!item || item.success === false) {
          return '';
        }
        let parts: string[] = [];
        // visitKey
        if (item.visitKey !== undefined) {
          parts.push(String(item.visitKey));
        }
        // links: [index] text （不包含链接本身）
        if (item.links && Array.isArray(item.links) && item.links.length > 0) {
          const linksLines = item.links.map((link: any, index: number) => `[${index + 1}] ${link.text}`);
          parts.push(linksLines.join('\n'));
        }
        // content
        if (item.content !== undefined) {
          parts.push(String(item.content));
        }
        return parts.join('\n');
      };

      if (Array.isArray(result)) {
        const formatted = result.map(formatOne).filter(Boolean).join('\n');
        return formatted;
      }

      return formatOne(result);
    };
  }

  return {
    search_bing,
    search_baidu,
    search_sogou,
    search_quark,
    combined_search,
    wrap,
    main
  };
})();


exports.search_bing = various_search.wrap(various_search.search_bing);
exports.search_baidu = various_search.wrap(various_search.search_baidu);
exports.search_sogou = various_search.wrap(various_search.search_sogou);
exports.search_quark = various_search.wrap(various_search.search_quark);
exports.combined_search = various_search.wrap(various_search.combined_search);

exports.main = various_search.main;