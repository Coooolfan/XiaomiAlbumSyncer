import type {McpTokenPermission} from '../enums/';

export interface McpTokenCreatedResponse {
    readonly id: number;
    readonly name: string;
    readonly permission: McpTokenPermission;
    readonly createdAt: number;
    readonly token: string;
}
