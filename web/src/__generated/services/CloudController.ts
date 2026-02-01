import type {Executor} from '../';

/**
 * 云端控制器
 * 
 * 提供云端空间相关的API接口
 * 所有接口均需要用户登录认证
 */
export class CloudController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 获取云端空间使用情况
     * 
     * 获取指定小米账号的云端空间使用情况
     * 
     * @parameter {CloudControllerOptions['getCloudSpace']} options
     * - accountId 小米账号ID
     * @return 返回云端空间信息
     */
    readonly getCloudSpace: (options: CloudControllerOptions['getCloudSpace']) => Promise<
        CloudSpaceInfo
    > = async(options) => {
        let _uri = '/api/cloud/space/';
        _uri += encodeURIComponent(options.accountId);
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<CloudSpaceInfo>;
    }
}

export type CloudControllerOptions = {
    'getCloudSpace': {
        readonly accountId: number
    }
}

/**
 * 云端空间信息
 */
export interface CloudSpaceInfo {
    readonly totalQuota: number
    readonly used: number
    readonly galleryUsed: number
    readonly usagePercent: number
}
