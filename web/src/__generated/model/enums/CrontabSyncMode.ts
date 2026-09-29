export const CrontabSyncMode_CONSTANTS = [
    /**
     * 每次同步枚举所选相册的全部资产
     */
    'FULL', 
    /**
     * 对比上一次同步的相册时间线，仅拉取有变动的日期
     */
    'TIMELINE', 
    /**
     * 按相册水位位点做记录级增量拉取，支持断点续拉
     */
    'CURSOR'
] as const;
/**
 * 定时任务的资产元数据刷新模式
 */
export type CrontabSyncMode = typeof CrontabSyncMode_CONSTANTS[number];
