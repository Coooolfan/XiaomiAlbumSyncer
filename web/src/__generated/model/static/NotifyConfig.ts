export interface NotifyConfig {
    readonly url: string;
    readonly headers: {readonly [key:string]: string};
    readonly body: string;
}
