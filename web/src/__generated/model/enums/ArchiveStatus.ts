export const ArchiveStatus_CONSTANTS = [
    /**
     * 计划中
     */
    'PLANNING', 
    /**
     * 移动文件中
     */
    'MOVING_FILES', 
    /**
     * 删除云端中
     */
    'DELETING_CLOUD', 
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
 * 归档状态枚举
 */
export type ArchiveStatus = typeof ArchiveStatus_CONSTANTS[number];
