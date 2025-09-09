import type {Executor} from './';
import {AlbumsController, SystemConfigController, TokenController} from './services/';

export class Api {
    
    readonly albumsController: AlbumsController
    
    readonly systemConfigController: SystemConfigController
    
    readonly tokenController: TokenController
    
    constructor(executor: Executor) {
        this.albumsController = new AlbumsController(executor);
        this.systemConfigController = new SystemConfigController(executor);
        this.tokenController = new TokenController(executor);
    }
}