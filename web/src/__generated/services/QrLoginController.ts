import type {Executor} from '../';
import type {QrLoginSessionView, QrLoginStatusView} from '../model/static/';

/**
 * 小米账号扫码登录控制器
 * 
 * 提供扫码登录会话的创建与状态查询接口。
 * 扫码确认成功后，服务端自动创建或更新对应小米账号的 passToken 凭据。
 * 所有接口均需要用户登录认证。
 * 
 */
export class QrLoginController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 创建扫码登录会话
     * 
     * 向小米通行证发起扫码登录初始化，返回二维码图片地址供前端展示。
     * 会话创建后服务端在后台持有长轮询，直至用户扫码确认或二维码过期。
     * 
     * @return QrLoginSessionView 会话 ID 与二维码图片 URL
     * 
     */
    readonly create: () => Promise<
        QrLoginSessionView
    > = async() => {
        let _uri = '/api/account/qr-login';
        return (await this.executor({uri: _uri, method: 'POST'})) as Promise<QrLoginSessionView>;
    }
    
    /**
     * 查询扫码登录会话状态
     * 
     * 前端轮询此接口获取扫码进度。
     * status 为 SUCCESS 时表示扫码成功且账号凭据已写入（已存在的 userId 会更新其 passToken）。
     * 
     * @parameter {QrLoginControllerOptions['status']} options
     * - sessionId 创建会话时返回的会话 ID
     * @return QrLoginStatusView 会话状态：WAITING / SUCCESS / EXPIRED / FAILED
     * 
     */
    readonly status: (options: QrLoginControllerOptions['status']) => Promise<
        QrLoginStatusView
    > = async(options) => {
        let _uri = '/api/account/qr-login/';
        _uri += encodeURIComponent(options.sessionId);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<QrLoginStatusView>;
    }
}

export type QrLoginControllerOptions = {
    'create': {}, 
    'status': {
        /**
         * 创建会话时返回的会话 ID
         */
        readonly sessionId: string
    }
}
