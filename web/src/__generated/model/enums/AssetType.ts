export const AssetType_CONSTANTS = [
    'IMAGE', 
    'VIDEO', 
    'AUDIO'
] as const;
export type AssetType = typeof AssetType_CONSTANTS[number];
