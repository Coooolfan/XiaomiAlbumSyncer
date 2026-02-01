export const SyncStatus_CONSTANTS = [
    /**
     * 运行中
     */
    'RUNNING', 
    /**
     * 已完成
     */
    'COMPLETED', 
    /**
     * 失败
     */
    'FAILED'
] as const;
/**
 * 同步状态枚举
 */
export type SyncStatus = typeof SyncStatus_CONSTANTS[number];
