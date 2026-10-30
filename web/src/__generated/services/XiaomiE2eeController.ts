import type {Executor} from '../';
import type {XiaomiE2eeStatus} from '../model/static/';

export class XiaomiE2eeController {

    constructor(private executor: Executor) {}

    readonly push: (options: XiaomiE2eeControllerOptions['push']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/xiaomi/e2ee/';
        _uri += encodeURIComponent(options.id);
        _uri += '/captcha';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<void>;
    }

    readonly status: (options: XiaomiE2eeControllerOptions['status']) => Promise<
        XiaomiE2eeStatus
    > = async(options) => {
        let _uri = '/api/xiaomi/e2ee/';
        _uri += encodeURIComponent(options.id);
        _uri += '/status';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<XiaomiE2eeStatus>;
    }

    readonly verify: (options: XiaomiE2eeControllerOptions['verify']) => Promise<
        XiaomiE2eeStatus
    > = async(options) => {
        let _uri = '/api/xiaomi/e2ee/';
        _uri += encodeURIComponent(options.id);
        _uri += '/verify';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<XiaomiE2eeStatus>;
    }
}

export type XiaomiE2eeControllerOptions = {
    'status': {
        readonly id: number
    },
    'push': {
        readonly id: number
    },
    'verify': {
        readonly id: number,
        readonly body: {readonly [key:string]: string}
    }
}
