export const SyncMode_CONSTANTS = [
    'ADD_ONLY', 
    'SYNC_ALL_CHANGES'
] as const;
export type SyncMode = typeof SyncMode_CONSTANTS[number];
