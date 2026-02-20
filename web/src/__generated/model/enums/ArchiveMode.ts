export const ArchiveMode_CONSTANTS = [
    'DISABLED', 
    'TIME', 
    'SPACE'
] as const;
export type ArchiveMode = typeof ArchiveMode_CONSTANTS[number];
