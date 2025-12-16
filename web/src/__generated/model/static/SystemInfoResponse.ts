/**
 * 系统信息响应数据类
 * 
 */
export interface SystemInfoResponse {
    /**
     * 是否运行在AOT运行时
     */
    readonly aotRuntime: boolean;
    /**
     * 是否运行在原生镜像中
     */
    readonly nativeImage: boolean;
    /**
     * JVM版本号
     */
    readonly jvmVersion?: string | undefined;
    /**
     * 应用程序版本号
     */
    readonly appVersion: string;
}
