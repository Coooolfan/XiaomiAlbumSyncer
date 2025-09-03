import type {Executor} from '../';
import type {CreateConfigRequest, IsInitResponse} from '../model/static/';

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
}

export type SystemConfigControllerOptions = {
    'isInit': {}, 
    'createConfig': {
        readonly body: CreateConfigRequest
    }
}
