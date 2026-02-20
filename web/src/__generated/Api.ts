import type {Executor} from './';
import {
    AlbumsController, 
    ArchiveController, 
    AssetController, 
    CloudController, 
    CrontabController, 
    PasskeyController, 
    SyncController, 
    SystemConfigController, 
    TokenController, 
    XiaomiAccountController
} from './services/';

export class Api {
    
    readonly albumsController: AlbumsController
    
    readonly archiveController: ArchiveController
    
    readonly assetController: AssetController
    
    readonly cloudController: CloudController
    
    readonly crontabController: CrontabController
    
    readonly passkeyController: PasskeyController
    
    readonly syncController: SyncController
    
    readonly systemConfigController: SystemConfigController
    
    readonly tokenController: TokenController
    
    readonly xiaomiAccountController: XiaomiAccountController
    
    constructor(executor: Executor) {
        this.albumsController = new AlbumsController(executor);
        this.archiveController = new ArchiveController(executor);
        this.assetController = new AssetController(executor);
        this.cloudController = new CloudController(executor);
        this.crontabController = new CrontabController(executor);
        this.passkeyController = new PasskeyController(executor);
        this.syncController = new SyncController(executor);
        this.systemConfigController = new SystemConfigController(executor);
        this.tokenController = new TokenController(executor);
        this.xiaomiAccountController = new XiaomiAccountController(executor);
    }
}