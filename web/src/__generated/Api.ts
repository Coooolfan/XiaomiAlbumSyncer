import type {Executor} from './';
import {
    AlbumsController, 
    AssetController, 
    CrontabController, 
    McpTokenController, 
    PasskeyController, 
    QrLoginController, 
    SystemConfigController, 
    TokenController, 
    XiaomiAccountController
} from './services/';

export class Api {
    
    readonly albumsController: AlbumsController
    
    readonly assetController: AssetController
    
    readonly crontabController: CrontabController
    
    readonly mcpTokenController: McpTokenController
    
    readonly passkeyController: PasskeyController
    
    readonly qrLoginController: QrLoginController
    
    readonly systemConfigController: SystemConfigController
    
    readonly tokenController: TokenController
    
    readonly xiaomiAccountController: XiaomiAccountController
    
    constructor(executor: Executor) {
        this.albumsController = new AlbumsController(executor);
        this.assetController = new AssetController(executor);
        this.crontabController = new CrontabController(executor);
        this.mcpTokenController = new McpTokenController(executor);
        this.passkeyController = new PasskeyController(executor);
        this.qrLoginController = new QrLoginController(executor);
        this.systemConfigController = new SystemConfigController(executor);
        this.tokenController = new TokenController(executor);
        this.xiaomiAccountController = new XiaomiAccountController(executor);
    }
}