export const ArchiveMode_CONSTANTS = [
    /**
     * 关闭归档
     */
    'DISABLED', 
    /**
     * 基于时间
     */
    'TIME', 
    /**
     * 基于空间阈值
     */
    'SPACE'
] as const;
/**
 * 归档模式枚举
 */
export type ArchiveMode = typeof ArchiveMode_CONSTANTS[number];
