export const SyncMode_CONSTANTS = [
    /**
     * 仅新增模式：只下载云端新增的文件到本地
     */
    'ADD_ONLY', 
    /**
     * 同步所有变化模式：同步云端的新增、修改、删除到本地
     */
    'SYNC_ALL_CHANGES'
] as const;
/**
 * 同步模式枚举
 */
export type SyncMode = typeof SyncMode_CONSTANTS[number];
