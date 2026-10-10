import type {CloudProvider} from '../enums/';

export type ProviderAccountDto = {
    'ProviderAccountController/DEFAULT_PROVIDER_ACCOUNT': {
        readonly id: number;
        readonly provider: CloudProvider;
        readonly nickname: string;
        readonly userId: string;
    }
}
