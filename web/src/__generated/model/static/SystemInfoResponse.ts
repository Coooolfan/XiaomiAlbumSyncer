export interface SystemInfoResponse {
    readonly aotRuntime: boolean;
    readonly nativeImage: boolean;
    readonly jvmVersion?: string | undefined;
    readonly appVersion: string;
}
