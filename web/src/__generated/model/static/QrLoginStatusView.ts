import type {QrLoginStatus} from '../enums/';

export interface QrLoginStatusView {
    readonly status: QrLoginStatus;
    readonly nickname?: string | undefined;
    readonly userId?: string | undefined;
    readonly error?: string | undefined;
}
