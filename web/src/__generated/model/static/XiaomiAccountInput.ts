/**
 * 小米登录输入；凭据只写入，不通过账号 API 返回。
 */
export interface XiaomiAccountInput {
    readonly nickname: string;
    readonly passToken: string;
    readonly userId: string;
}
