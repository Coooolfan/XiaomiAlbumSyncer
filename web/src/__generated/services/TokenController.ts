import type {Executor} from '../';

export class TokenController {
    
    constructor(private executor: Executor) {}
    
    readonly login: (options: TokenControllerOptions['login']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/token';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.login;
        _uri += _separator
        _uri += 'login='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<void>;
    }
    
    readonly logout: () => Promise<
        void
    > = async() => {
        let _uri = '/api/token';
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
}

export type TokenControllerOptions = {
    'login': {
        readonly login: string
    }, 
    'logout': {}
}
