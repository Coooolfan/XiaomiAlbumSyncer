import type {Executor} from '../';

/**
 * 令牌管理控制器
 * 
 * 提供用户登录认证相关的API接口，包括用户登录、登出等功能
 * 登录接口为公开接口，无需认证；登出接口需要用户登录认证（通过方法级别注解控制）
 * 
 */
export class TokenController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 用户登录接口
     *  
     * 此接口用于用户登录认证，验证密码后生成并返回认证令牌
     * 无需登录认证即可访问（公开接口）
     *  
     * @parameter {TokenControllerOptions['login']} options
     * - password 用户密码，用于登录认证
     *  
     */
    readonly login: (options: TokenControllerOptions['login']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/token';
        let _separator = _uri.indexOf('?') === -1 ? '?' : '&';
        let _value: any = undefined;
        _value = options.password;
        _uri += _separator
        _uri += 'password='
        _uri += encodeURIComponent(_value);
        _separator = '&';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<void>;
    }
    
    /**
     * 用户登出接口
     *  
     * 此接口用于用户登出，清除当前会话的认证信息
     * 无需登录认证即可访问（公开接口）
     *  
     */
    readonly logout: () => Promise<
        void
    > = async() => {
        const _uri = '/api/token';
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
}

export type TokenControllerOptions = {
    'login': {
        /**
         * 用户密码，用于登录认证
         *  
         */
        readonly password: string
    }, 
    'logout': {}
}
