export interface Page<T> {
    readonly rows: ReadonlyArray<T>;
    readonly totalPageCount: number;
    readonly totalRowCount: number;
}
