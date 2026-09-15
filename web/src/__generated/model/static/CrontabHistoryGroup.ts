/**
 * 折叠后的定时任务执行历史分组
 * 
 */
export interface CrontabHistoryGroup {
    /**
     * 组内最新一次执行的历史 ID
     */
    readonly historyId: number;
    /**
     * 组内最早的开始时间
     */
    readonly startTime: string;
    /**
     * 组内最晚的结束时间（进行中为 null）
     */
    readonly endTime?: string | undefined;
    /**
     * 折叠的执行次数，1 表示未折叠的单条记录
     */
    readonly runCount: number;
    /**
     * 组内资产明细总数
     */
    readonly detailsCount: number;
}
