export const AssetType_CONSTANTS = [
    'IMAGE', 
    'VIDEO'
] as const;
export type AssetType = typeof AssetType_CONSTANTS[number];
