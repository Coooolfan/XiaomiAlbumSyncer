import type {Executor} from '../';
import type {
    HasPasskeysResponse, 
    PasskeyAuthFinishRequest, 
    PasskeyAuthStartResponse, 
    PasskeyCredentialInfo, 
    PasskeyRegisterFinishRequest, 
    PasskeyRegisterStartRequest, 
    PasskeyRegisterStartResponse, 
    PasskeyUpdateNameRequest
} from '../model/static/';

/**
 * Passkey 管理控制器
 * 
 * 提供 Passkey 注册、认证与管理相关的API接口
 * 注册与管理接口需要用户登录认证；认证接口为公开接口
 * 
 */
export class PasskeyController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 删除 Passkey 接口
     * 
     * 此接口用于删除指定 Passkey
     * 需要用户登录认证才能访问
     * 
     * @parameter {PasskeyControllerOptions['deleteCredential']} options
     * - credentialId Passkey 凭据ID
     * 
     */
    readonly deleteCredential: (options: PasskeyControllerOptions['deleteCredential']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/passkey/';
        _uri += encodeURIComponent(options.credentialId);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
    
    /**
     * 完成 Passkey 认证接口
     * 
     * 此接口用于验证 Passkey 认证结果并登录用户
     * 无需登录认证即可访问（公开接口）
     * 
     * @parameter {PasskeyControllerOptions['finishAuthentication']} options
     * - request Passkey 认证完成请求参数
     * 
     */
    readonly finishAuthentication: (options: PasskeyControllerOptions['finishAuthentication']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/passkey/authenticate/finish';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * 完成 Passkey 注册接口
     * 
     * 此接口用于提交注册结果并保存 Passkey 凭据
     * 需要用户登录认证才能访问
     * 
     * @parameter {PasskeyControllerOptions['finishRegistration']} options
     * - request Passkey 注册完成请求参数
     * @return PasskeyCredentialInfo 返回已保存的 Passkey 信息
     * 
     */
    readonly finishRegistration: (options: PasskeyControllerOptions['finishRegistration']) => Promise<
        PasskeyCredentialInfo
    > = async(options) => {
        let _uri = '/api/passkey/register/finish';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<PasskeyCredentialInfo>;
    }
    
    /**
     * 检查是否已注册 Passkey 接口
     * 
     * 此接口用于判断当前系统是否已注册 Passkey
     * 无需登录认证即可访问（公开接口）
     * 
     * @return HasPasskeysResponse 返回是否存在 Passkey 的标记
     * 
     */
    readonly hasPasskeys: () => Promise<
        HasPasskeysResponse
    > = async() => {
        let _uri = '/api/passkey/available';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<HasPasskeysResponse>;
    }
    
    /**
     * 获取已注册的 Passkey 列表接口
     * 
     * 此接口用于查询当前系统已注册的 Passkey 列表
     * 需要用户登录认证才能访问
     * 
     * @return List<PasskeyCredentialInfo> 返回已注册的 Passkey 列表
     * 
     */
    readonly listCredentials: () => Promise<
        ReadonlyArray<PasskeyCredentialInfo>
    > = async() => {
        let _uri = '/api/passkey';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<PasskeyCredentialInfo>>;
    }
    
    /**
     * 开始 Passkey 认证接口
     * 
     * 此接口用于发起 Passkey 认证，返回浏览器所需的 WebAuthn 选项
     * 无需登录认证即可访问（公开接口）
     * 
     * @return PasskeyAuthStartResponse 返回认证选项与会话信息
     * 
     */
    readonly startAuthentication: () => Promise<
        PasskeyAuthStartResponse
    > = async() => {
        let _uri = '/api/passkey/authenticate/start';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<PasskeyAuthStartResponse>;
    }
    
    /**
     * 开始 Passkey 注册接口
     * 
     * 此接口用于发起 Passkey 注册，验证密码后生成注册挑战与选项
     * 需要用户登录认证才能访问
     * 
     * @parameter {PasskeyControllerOptions['startRegistration']} options
     * - request Passkey 注册请求参数，包含密码与凭据名称
     * @return PasskeyRegisterStartResponse 返回注册选项与会话信息
     * 
     */
    readonly startRegistration: (options: PasskeyControllerOptions['startRegistration']) => Promise<
        PasskeyRegisterStartResponse
    > = async(options) => {
        let _uri = '/api/passkey/register/start';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<PasskeyRegisterStartResponse>;
    }
    
    /**
     * 更新 Passkey 名称接口
     * 
     * 此接口用于更新指定 Passkey 的名称
     * 需要用户登录认证才能访问
     * 
     * @parameter {PasskeyControllerOptions['updateCredentialName']} options
     * - credentialId Passkey 凭据ID
     * - request Passkey 名称更新请求参数
     * 
     */
    readonly updateCredentialName: (options: PasskeyControllerOptions['updateCredentialName']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/passkey/';
        _uri += encodeURIComponent(options.credentialId);
        _uri += '/name';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
}

export type PasskeyControllerOptions = {
    'startRegistration': {
        /**
         * Passkey 注册请求参数，包含密码与凭据名称
         */
        readonly body: PasskeyRegisterStartRequest
    }, 
    'finishRegistration': {
        /**
         * Passkey 注册完成请求参数
         */
        readonly body: PasskeyRegisterFinishRequest
    }, 
    'startAuthentication': {}, 
    'finishAuthentication': {
        /**
         * Passkey 认证完成请求参数
         * 
         */
        readonly body: PasskeyAuthFinishRequest
    }, 
    'listCredentials': {}, 
    'deleteCredential': {
        /**
         * Passkey 凭据ID
         * 
         */
        readonly credentialId: string
    }, 
    'updateCredentialName': {
        /**
         * Passkey 凭据ID
         */
        readonly credentialId: string, 
        /**
         * Passkey 名称更新请求参数
         * 
         */
        readonly body: PasskeyUpdateNameRequest
    }, 
    'hasPasskeys': {}
}
