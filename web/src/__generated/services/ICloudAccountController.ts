import type {Executor} from '../';
import type {ICloudAccountStatus, ICloudLoginInput, ICloudVerifyInput} from '../model/static/';

export class ICloudAccountController {

    constructor(private executor: Executor) {}

    readonly login: (options: ICloudAccountControllerOptions['login']) => Promise<
        ICloudAccountStatus
    > = async(options) => {
        let _uri = '/api/icloud/login';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<ICloudAccountStatus>;
    }

    readonly status: (options: ICloudAccountControllerOptions['status']) => Promise<
        ICloudAccountStatus
    > = async(options) => {
        let _uri = '/api/icloud/';
        _uri += encodeURIComponent(options.id);
        _uri += '/status';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ICloudAccountStatus>;
    }

    readonly verify: (options: ICloudAccountControllerOptions['verify']) => Promise<
        ICloudAccountStatus
    > = async(options) => {
        let _uri = '/api/icloud/';
        _uri += encodeURIComponent(options.id);
        _uri += '/verify';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<ICloudAccountStatus>;
    }
}

export type ICloudAccountControllerOptions = {
    'login': {
        readonly body: ICloudLoginInput
    },
    'verify': {
        readonly id: number,
        readonly body: ICloudVerifyInput
    },
    'status': {
        readonly id: number
    }
}
