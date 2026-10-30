import type {Executor} from './';
import {
    AlbumsController,
    AssetController,
    AuthController,
    CrontabController,
    ICloudAccountController,
    McpTokenController,
    PasskeyController,
    ProviderAccountController,
    SystemConfigController,
    XiaomiE2eeController,
    XiaomiQrLoginController
} from './services/';

export class Api {

    readonly albumsController: AlbumsController

    readonly assetController: AssetController

    readonly authController: AuthController

    readonly crontabController: CrontabController

    readonly icloudAccountController: ICloudAccountController

    readonly mcpTokenController: McpTokenController

    readonly passkeyController: PasskeyController

    readonly providerAccountController: ProviderAccountController

    readonly systemConfigController: SystemConfigController

    readonly xiaomiE2eeController: XiaomiE2eeController

    readonly xiaomiQrLoginController: XiaomiQrLoginController

    constructor(executor: Executor) {
        this.albumsController = new AlbumsController(executor);
        this.assetController = new AssetController(executor);
        this.authController = new AuthController(executor);
        this.crontabController = new CrontabController(executor);
        this.icloudAccountController = new ICloudAccountController(executor);
        this.mcpTokenController = new McpTokenController(executor);
        this.passkeyController = new PasskeyController(executor);
        this.providerAccountController = new ProviderAccountController(executor);
        this.systemConfigController = new SystemConfigController(executor);
        this.xiaomiE2eeController = new XiaomiE2eeController(executor);
        this.xiaomiQrLoginController = new XiaomiQrLoginController(executor);
    }
}
