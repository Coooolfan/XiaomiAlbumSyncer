import type {Executor} from './';
import {SystemConfigController, TokenController} from './services/';

export class Api {
    
    readonly systemConfigController: SystemConfigController
    
    readonly tokenController: TokenController
    
    constructor(executor: Executor) {
        this.systemConfigController = new SystemConfigController(executor);
        this.tokenController = new TokenController(executor);
    }
}