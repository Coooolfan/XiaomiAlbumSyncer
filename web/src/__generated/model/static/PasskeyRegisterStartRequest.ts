/**
 * Passkey 注册请求数据类
 * 
 */
export interface PasskeyRegisterStartRequest {
    /**
     * 当前登录密码
     */
    readonly password: string;
    /**
     * Passkey 名称
     */
    readonly credentialName: string;
}
