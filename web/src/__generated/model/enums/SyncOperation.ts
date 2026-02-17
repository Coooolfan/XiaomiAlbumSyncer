export const SyncOperation_CONSTANTS = [
    /**
     * 新增
     */
    'ADD', 
    /**
     * 删除
     */
    'DELETE', 
    /**
     * 更新
     */
    'UPDATE'
] as const;
/**
 * 同步操作类型枚举
 */
export type SyncOperation = typeof SyncOperation_CONSTANTS[number];
