export const QrLoginStatus_CONSTANTS = [
    'WAITING', 
    'SUCCESS', 
    'EXPIRED', 
    'FAILED'
] as const;
export type QrLoginStatus = typeof QrLoginStatus_CONSTANTS[number];
