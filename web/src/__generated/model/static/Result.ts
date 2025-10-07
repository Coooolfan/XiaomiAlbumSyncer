export interface Result<T> {
    readonly SUCCEED_CODE: number;
    readonly FAILURE_CODE: number;
    readonly code: number;
    readonly description: string;
    readonly data: T;
}
