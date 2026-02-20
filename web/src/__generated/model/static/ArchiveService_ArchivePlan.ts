import type {Dynamic_Asset} from '../dynamic/';

export interface ArchiveService_ArchivePlan {
    readonly archiveBeforeDate: string;
    readonly assetsToArchive: ReadonlyArray<Dynamic_Asset>;
    readonly estimatedFreedSpace: number;
}
