import type {Executor} from '../';
import type {SystemConfigDto} from '../model/dto/';
import type {
    IsInitResponse, 
    MountPathCheckRequest, 
    MountPathCheckResponse, 
    SystemConfigFtqqKeyIsInitResponse, 
    SystemConfigFtqqKeyUpdate, 
    SystemConfigInit, 
    SystemConfigPasswordUpdate, 
    SystemConfigUpdate, 
    SystemInfoResponse
} from '../model/static/';

/**
 * 系统配置管理控制器
 * 
 * 提供系统配置相关的API接口，包括系统初始化、配置更新、密码管理、系统信息查询等功能
 * 部分接口需要用户登录认证（通过方法级别注解 @SaCheckLogin 控制）
 * 
 */
export class SystemConfigController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 检查路径是否为挂载点
     * 
     * 此接口用于判断传入绝对路径是否为明确挂载点
     * 非 Docker 运行环境固定返回 false
     * 需要用户登录认证才能访问
     * 
     * @parameter {SystemConfigControllerOptions['checkMountPath']} options
     * - request 路径检查请求，包含待检查的绝对路径
     * @return MountPathCheckResponse 挂载点检查结果
     * 
     */
    readonly checkMountPath: (options: SystemConfigControllerOptions['checkMountPath']) => Promise<
        MountPathCheckResponse
    > = async(options) => {
        let _uri = '/api/system-config/mount-path';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<MountPathCheckResponse>;
    }
    
    /**
     * 获取 Server酱（FTQQ）推送Key初始化状态
     * 
     * 此接口用于检查是否已配置 Server酱（FTQQ）推送Key
     * 需要用户登录认证才能访问
     * 
     * @return SystemConfigFtqqKeyIsInitResponse 返回初始化状态，包含布尔值表示是否已配置
     * 
     */
    readonly ftqqKeyIsInitd: () => Promise<
        SystemConfigFtqqKeyIsInitResponse
    > = async() => {
        let _uri = '/api/system-config/ftqq-key';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<SystemConfigFtqqKeyIsInitResponse>;
    }
    
    /**
     * 获取普通系统配置
     * 
     * 此接口用于获取系统的普通配置信息（如exif工具路径等）
     * 需要用户登录认证才能访问
     * 
     * @return SystemConfig 返回系统的普通配置信息
     * 
     */
    readonly getSystemConfig: () => Promise<
        SystemConfigDto['SystemConfigController/NORMAL_SYSTEM_CONFIG']
    > = async() => {
        let _uri = '/api/system-config/normal';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<SystemConfigDto['SystemConfigController/NORMAL_SYSTEM_CONFIG']>;
    }
    
    /**
     * 获取系统调试信息
     * 
     * 此接口用于获取系统的详细调试信息，用于问题诊断和系统监控
     * 需要用户登录认证才能访问
     * 
     * @return String 返回系统调试信息字符串
     * 
     */
    readonly getSystemDebugInfo: () => Promise<
        string
    > = async() => {
        let _uri = '/api/system-config/info/debug';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<string>;
    }
    
    /**
     * 获取系统信息
     * 
     * 此接口用于获取当前系统的运行时信息，包括JVM版本、AOT运行时状态等
     * 需要用户登录认证才能访问
     * 
     * @return SystemInfoResponse 返回系统信息响应，包含AOT运行时、原生镜像、JVM版本等信息
     * 
     */
    readonly getSystemInfo: () => Promise<
        SystemInfoResponse
    > = async() => {
        let _uri = '/api/system-config/info';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<SystemInfoResponse>;
    }
    
    /**
     * 从旧版本数据库导入数据
     * 
     * 此接口用于从旧版本的数据库中导入数据到当前系统
     * 需要用户登录认证才能访问
     * 
     */
    readonly importFromV2Db: () => Promise<
        void
    > = async() => {
        let _uri = '/api/system-config/import-from-v2';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<void>;
    }
    
    /**
     * 初始化系统配置
     * 
     * 此接口用于首次设置系统的基本配置信息
     * 无需登录认证即可访问（公开接口）
     * 
     * @parameter {SystemConfigControllerOptions['initConfig']} options
     * - create 系统配置初始化参数，包含初始配置信息
     * 
     */
    readonly initConfig: (options: SystemConfigControllerOptions['initConfig']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/system-config';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * 检查系统是否已完成初始化
     * 
     * 此接口用于检查系统配置是否已经完成初始化设置
     * 无需登录认证即可访问（公开接口）
     * 
     * @return IsInitResponse 返回初始化状态，包含布尔值表示是否已初始化
     * 
     */
    readonly isInit: () => Promise<
        IsInitResponse
    > = async() => {
        let _uri = '/api/system-config';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<IsInitResponse>;
    }
    
    /**
     * 更新 Server酱（FTQQ）推送Key
     * 
     * 此接口用于更新/设置 Server酱（FTQQ）推送Key
     * 需要用户登录认证才能访问
     * 
     * @parameter {SystemConfigControllerOptions['updateFtqqKey']} options
     * - update 推送Key更新参数，包含新的推送Key信息
     * 
     */
    readonly updateFtqqKey: (options: SystemConfigControllerOptions['updateFtqqKey']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/system-config/ftqq-key';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * 更新用户密码
     * 
     * 此接口用于更新系统的用户登录密码
     * 需要用户登录认证才能访问
     * 
     * @parameter {SystemConfigControllerOptions['updatePassword']} options
     * - update 密码更新参数，包含新的密码信息
     * 
     */
    readonly updatePassword: (options: SystemConfigControllerOptions['updatePassword']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/system-config/password';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<void>;
    }
    
    /**
     * 更新普通系统配置
     * 
     * 此接口用于更新系统的普通配置信息（如exif工具路径等）
     * 需要用户登录认证才能访问
     * 
     * @parameter {SystemConfigControllerOptions['updateSystemConfig']} options
     * - update 系统配置更新参数，包含新的配置信息
     * 
     */
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
        /**
         * 系统配置初始化参数，包含初始配置信息
         * 
         */
        readonly body: SystemConfigInit
    }, 
    'updateSystemConfig': {
        /**
         * 系统配置更新参数，包含新的配置信息
         * 
         */
        readonly body: SystemConfigUpdate
    }, 
    'getSystemConfig': {}, 
    'updatePassword': {
        /**
         * 密码更新参数，包含新的密码信息
         * 
         */
        readonly body: SystemConfigPasswordUpdate
    }, 
    'getSystemInfo': {}, 
    'getSystemDebugInfo': {}, 
    'checkMountPath': {
        /**
         * 路径检查请求，包含待检查的绝对路径
         */
        readonly body: MountPathCheckRequest
    }, 
    'importFromV2Db': {}, 
    'ftqqKeyIsInitd': {}, 
    'updateFtqqKey': {
        /**
         * 推送Key更新参数，包含新的推送Key信息
         * 
         */
        readonly body: SystemConfigFtqqKeyUpdate
    }
}
