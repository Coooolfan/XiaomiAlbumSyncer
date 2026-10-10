export const CloudProvider_CONSTANTS = [
    'XIAOMI',
    'ICLOUD'
] as const;
export type CloudProvider = typeof CloudProvider_CONSTANTS[number];
