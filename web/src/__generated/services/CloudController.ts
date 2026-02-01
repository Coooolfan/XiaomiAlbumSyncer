import type {Executor} from '../';
import type {CloudSpaceInfo} from '../model/static/';

/**
 * 云端控制器
 * 提供云端相关功能 API
 */
export class CloudController {
    
    constructor(private executor: Executor) {}
    
    /**
     * 获取云端空间使用情况
     * 
     * @parameter {CloudControllerOptions['getCloudSpace']} options
     * - accountId 账号 ID
     * @return 云端空间信息
     * 
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
        /**
         * 账号 ID
         */
        readonly accountId: number
    }
}
