/* METADATA
{
  name: time
  description: 提供时间相关功能。实际上，激活本包的同时已经能够获取时间了。
  enabledByDefault: true
  
  tools: [
    {
      name: get_time
      description: 获取当前时间。当使用此包时，AI已经自动获取了当前的时间信息。
      parameters: []
    },
    {
      name: format_time
      description: 格式化时间。提供各种时间格式化选项。
      parameters: []
    }
  ]
}
*/

const timePackage = (function () {
  // 获取当前时间
  const getCurrentTime = () => {
    return new Date();
  };

  // 格式化时间为易读格式
  const formatReadable = (date = new Date()) => {
    return date.toLocaleString();
  };

  // ISO格式
  const formatISO = (date = new Date()) => {
    return date.toISOString();
  };

  // 格式化为YYYY-MM-DD
  const formatDate = (date = new Date()) => {
    return date.toISOString().split('T')[0];
  };

  // 格式化为HH:MM:SS
  const formatTime = (date = new Date()) => {
    return date.toTimeString().split(' ')[0];
  };

  return {
    main: async () => {
      const now = getCurrentTime();
      return `当前时间: ${formatReadable(now)}`;
    },
    getCurrentTime,
    formatReadable,
    formatISO,
    formatDate,
    formatTime
  };
})();

// 逐个导出
exports.main = timePackage.main;
exports.getCurrentTime = timePackage.getCurrentTime;
exports.formatReadable = timePackage.formatReadable;
exports.formatISO = timePackage.formatISO;
exports.formatDate = timePackage.formatDate;
exports.formatTime = timePackage.formatTime; 