import type {Executor} from './';
import {
    AlbumsController,
    AssetController,
    CrontabController,
    ICloudController,
    McpTokenController,
    PasskeyController,
    ProviderAccountController,
    QrLoginController,
    SystemConfigController,
    TokenController
} from './services/';

export class Api {

    readonly albumsController: AlbumsController

    readonly assetController: AssetController

    readonly crontabController: CrontabController

    readonly icloudController: ICloudController

    readonly mcpTokenController: McpTokenController

    readonly passkeyController: PasskeyController

    readonly providerAccountController: ProviderAccountController

    readonly qrLoginController: QrLoginController

    readonly systemConfigController: SystemConfigController

    readonly tokenController: TokenController

    constructor(executor: Executor) {
        this.albumsController = new AlbumsController(executor);
        this.assetController = new AssetController(executor);
        this.crontabController = new CrontabController(executor);
        this.icloudController = new ICloudController(executor);
        this.mcpTokenController = new McpTokenController(executor);
        this.passkeyController = new PasskeyController(executor);
        this.providerAccountController = new ProviderAccountController(executor);
        this.qrLoginController = new QrLoginController(executor);
        this.systemConfigController = new SystemConfigController(executor);
        this.tokenController = new TokenController(executor);
    }
}
