export const XiaomiE2eeState_CONSTANTS = [
    'DISABLED',
    'VERIFICATION_REQUIRED',
    'READY',
    'SESSION_EXPIRED',
    'RATE_LIMITED'
] as const;
export type XiaomiE2eeState = typeof XiaomiE2eeState_CONSTANTS[number];
