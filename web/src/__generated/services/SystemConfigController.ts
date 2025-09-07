import type {Executor} from '../';
import type {IsInitResponse, SystemConfigInit, SystemConfigUpdate} from '../model/static/';

export class SystemConfigController {
    
    constructor(private executor: Executor) {}
    
    readonly createConfig: (options: SystemConfigControllerOptions['createConfig']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/system-config';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    readonly isInit: () => Promise<
        IsInitResponse
    > = async() => {
        let _uri = '/api/system-config';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<IsInitResponse>;
    }
    
    readonly updatePassToken: (options: SystemConfigControllerOptions['updatePassToken']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/system-config/pass-token';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
}

export type SystemConfigControllerOptions = {
    'isInit': {}, 
    'createConfig': {
        readonly body: SystemConfigInit
    }, 
    'updatePassToken': {
        readonly body: SystemConfigUpdate
    }
}
