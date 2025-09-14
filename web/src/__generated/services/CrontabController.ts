import type {Executor} from '../';
import type {CrontabDto} from '../model/dto/';
import type {CrontabInput, Result} from '../model/static/';

export class CrontabController {
    
    constructor(private executor: Executor) {}
    
    readonly createCrontab: (options: CrontabControllerOptions['createCrontab']) => Promise<
        CrontabDto['CrontabController/DEFAULT_CRONTAB']
    > = async(options) => {
        let _uri = '/api/crontab';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<CrontabDto['CrontabController/DEFAULT_CRONTAB']>;
    }
    
    readonly deleteCrontab: (options: CrontabControllerOptions['deleteCrontab']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/crontab/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    readonly executeCrontab: (options: CrontabControllerOptions['executeCrontab']) => Promise<
        Result<number>
    > = async(options) => {
        let _uri = '/api/crontab/';
        _uri += encodeURIComponent(options.crontabId);
        _uri += '/executions';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<Result<number>>;
    }
    
    readonly listCrontabs: () => Promise<
        ReadonlyArray<CrontabDto['CrontabController/DEFAULT_CRONTAB']>
    > = async() => {
        let _uri = '/api/crontab';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<CrontabDto['CrontabController/DEFAULT_CRONTAB']>>;
    }
    
    readonly updateCrontab: (options: CrontabControllerOptions['updateCrontab']) => Promise<
        CrontabDto['CrontabController/DEFAULT_CRONTAB']
    > = async(options) => {
        let _uri = '/api/crontab/';
        _uri += encodeURIComponent(options.crontabId);
        return (await this.executor({uri: _uri, method: 'PUT', body: options.body})) as Promise<CrontabDto['CrontabController/DEFAULT_CRONTAB']>;
    }
}

export type CrontabControllerOptions = {
    'listCrontabs': {}, 
    'createCrontab': {
        readonly body: CrontabInput
    }, 
    'updateCrontab': {
        readonly body: CrontabInput, 
        readonly crontabId: number
    }, 
    'deleteCrontab': {
        readonly crontabId: number
    }, 
    'executeCrontab': {
        readonly crontabId: number
    }
}
