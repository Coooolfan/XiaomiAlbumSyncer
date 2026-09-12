import type {Executor} from '../';
import type {McpTokenCreateRequest, McpTokenCreatedResponse, McpTokenInfo} from '../model/static/';

/**
 * MCP Token 管理接口。Token 由服务端生成，原文只在创建响应中返回一次。
 */
export class McpTokenController {

    constructor(private executor: Executor) {}

    readonly create: (options: McpTokenControllerOptions['create']) => Promise<
        McpTokenCreatedResponse
    > = async(options) => {
        let _uri = '/api/mcp-token';
        return (await this.executor({uri: _uri, method: 'POST', body: options.body})) as Promise<McpTokenCreatedResponse>;
    }

    readonly list: () => Promise<
        ReadonlyArray<McpTokenInfo>
    > = async() => {
        let _uri = '/api/mcp-token';
        return (await this.executor({uri: _uri, method: 'GET'})) as Promise<ReadonlyArray<McpTokenInfo>>;
    }

    readonly revoke: (options: McpTokenControllerOptions['revoke']) => Promise<
        void
    > = async(options) => {
        let _uri = '/api/mcp-token/';
        _uri += encodeURIComponent(options.id);
        return (await this.executor({uri: _uri, method: 'DELETE'})) as Promise<void>;
    }
}

export type McpTokenControllerOptions = {
    'list': {},
    'create': {
        readonly body: McpTokenCreateRequest
    },
    'revoke': {
        readonly id: number
    }
}
