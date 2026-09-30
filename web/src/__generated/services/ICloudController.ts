import type {Executor} from '../';
import type {ICloudAccountStatus, ICloudLoginInput, ICloudVerifyInput} from '../model/static/';

export class ICloudController {

    constructor(private executor: Executor) {}

    readonly login: (options: ICloudControllerOptions['login']) => Promise<
        ICloudAccountStatus
    > = async(options) => {
        let _uri = '/api/icloud/login';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<ICloudAccountStatus>;
    }

    readonly status: (options: ICloudControllerOptions['status']) => Promise<
        ICloudAccountStatus
    > = async(options) => {
        let _uri = '/api/icloud/';
        _uri += encodeURIComponent(options.id);
        _uri += '/status';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ICloudAccountStatus>;
    }

    readonly verify: (options: ICloudControllerOptions['verify']) => Promise<
        ICloudAccountStatus
    > = async(options) => {
        let _uri = '/api/icloud/';
        _uri += encodeURIComponent(options.id);
        _uri += '/verify';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<ICloudAccountStatus>;
    }
}

export type ICloudControllerOptions = {
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
