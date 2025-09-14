import type {Executor} from '../';
import type {SystemConfigDto} from '../model/dto/';
import type {
    IsInitResponse, 
    SystemConfigInit, 
    SystemConfigPassTokenUpdate, 
    SystemConfigUpdate
} from '../model/static/';

export class SystemConfigController {
    
    constructor(private executor: Executor) {}
    
    readonly getSystemConfig: () => Promise<
        SystemConfigDto['SystemConfigController/NORMAL_SYSTEM_CONFIG']
    > = async() => {
        let _uri = '/api/system-config/normal';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<SystemConfigDto['SystemConfigController/NORMAL_SYSTEM_CONFIG']>;
    }
    
    readonly initConfig: (options: SystemConfigControllerOptions['initConfig']) => Promise<
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
    
    readonly updateSystemConfig: (options: SystemConfigControllerOptions['updateSystemConfig']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/system-config/normal';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
}

export type SystemConfigControllerOptions = {
    'isInit': {}, 
    'initConfig': {
        readonly body: SystemConfigInit
    }, 
    'updatePassToken': {
        readonly body: SystemConfigPassTokenUpdate
    }, 
    'updateSystemConfig': {
        readonly body: SystemConfigUpdate
    }, 
    'getSystemConfig': {}
}
