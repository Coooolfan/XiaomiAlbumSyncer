import type {McpTokenPermission} from '../enums/';

export interface McpTokenCreateRequest {
    readonly name: string;
    readonly permission: McpTokenPermission;
}
