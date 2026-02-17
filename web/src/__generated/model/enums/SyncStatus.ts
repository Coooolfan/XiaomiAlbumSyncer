export const SyncStatus_CONSTANTS = [
    'RUNNING', 
    'COMPLETED', 
    'FAILED'
] as const;
export type SyncStatus = typeof SyncStatus_CONSTANTS[number];
