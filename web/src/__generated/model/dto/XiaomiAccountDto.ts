import type {CloudProvider} from '../enums/';

export type XiaomiAccountDto = {
    'XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT': {
        readonly id: number;
        readonly provider: CloudProvider;
        readonly nickname: string;
        readonly userId: string;
    }
}
