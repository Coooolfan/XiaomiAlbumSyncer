export const ArchiveStatus_CONSTANTS = [
    'PLANNING', 
    'MOVING_FILES', 
    'DELETING_CLOUD', 
    'COMPLETED', 
    'FAILED'
] as const;
export type ArchiveStatus = typeof ArchiveStatus_CONSTANTS[number];
