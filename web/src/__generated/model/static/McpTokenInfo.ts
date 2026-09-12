import type {McpTokenPermission} from '../enums/';

export interface McpTokenInfo {
    readonly id: number;
    readonly name: string;
    readonly permission: McpTokenPermission;
    readonly createdAt: number;
}
