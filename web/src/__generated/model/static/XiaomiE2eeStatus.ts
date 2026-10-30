import type {XiaomiE2eeState} from '../enums/';

export interface XiaomiE2eeStatus {
    readonly accountId: number;
    readonly state: XiaomiE2eeState;
    readonly cachedKeyCount: number;
}
