export const McpTokenPermission_CONSTANTS = [
    'READ_ONLY',
    'ALLOW_TRIGGER'
] as const;
export type McpTokenPermission = typeof McpTokenPermission_CONSTANTS[number];
