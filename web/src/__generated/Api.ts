import type {Executor} from './';
import {
    AlbumsController, 
    AssetController, 
    CrontabController, 
    SystemConfigController, 
    TokenController, 
    XiaomiAccountController
} from './services/';

export class Api {
    
    readonly albumsController: AlbumsController
    
    readonly assetController: AssetController
    
    readonly crontabController: CrontabController
    
    readonly systemConfigController: SystemConfigController
    
    readonly tokenController: TokenController
    
    readonly xiaomiAccountController: XiaomiAccountController
    
    constructor(executor: Executor) {
        this.albumsController = new AlbumsController(executor);
        this.assetController = new AssetController(executor);
        this.crontabController = new CrontabController(executor);
        this.systemConfigController = new SystemConfigController(executor);
        this.tokenController = new TokenController(executor);
        this.xiaomiAccountController = new XiaomiAccountController(executor);
    }
}