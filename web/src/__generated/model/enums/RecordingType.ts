export const RecordingType_CONSTANTS = [
    'RECORDER', 
    'PHONE_CALL', 
    'FM', 
    'APP', 
    'UNKNOWN'
] as const;
export type RecordingType = typeof RecordingType_CONSTANTS[number];
