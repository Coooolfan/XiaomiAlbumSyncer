import asyncio
from datetime import datetime
import click

import nest_asyncio
from InquirerPy import inquirer

from src.configer import Configer
from src.task import (modify_config, update_album_list as task_update_album_list, set_cookie as task_set_cookie, 
                      config_selected_album, download_selected_album as task_download_selected_album,
                      empty_download_record as task_empty_download_record, select_and_download_single_album, 
                      exit_syncer, refresh_cookie as task_refresh_cookie)

nest_asyncio.apply()


# 交互式
async def icli():
    Configer.set("endDate", datetime.now().strftime("%Y%m%d"))
    while True:
        task = inquirer.select(
            message="欢迎回来，准备干些什么捏",
            choices=["设置cookie", 
                     "更新cookie", 
                     "更新相册列表", 
                     "选择要下载的相册", 
                     "下载已选择的相册", 
                     "修改配置",
                     "下载单个相册",
                     "清空下载记录", 
                     "退出"],
        ).execute()

        if task == "设置cookie":
            task_set_cookie()
        elif task == "更新cookie":
            await task_refresh_cookie()
        elif task == "更新相册列表":
            await task_update_album_list()
        elif task == "选择要下载的相册":
            config_selected_album()
        elif task == "下载已选择的相册":
            check = inquirer.confirm(
                message="确认开始下载已选择的相册吗？\n此操作可能会花费大量时间，如果需要测试配置是否正确请选择‘下载单个相册’",
                default=True).execute()
            if check:
                await task_download_selected_album()
        elif task == "修改配置":
            modify_config()
        elif task == "下载单个相册":
            await select_and_download_single_album()
        elif task == "清空下载记录":
            check = inquirer.confirm(
                message="确认清空下载记录吗？\n清空后的第一次下载会下载所有媒体文件并复盖原文件",
                default=True).execute()
            if check:
                await task_empty_download_record()
        elif task == "退出":
            await exit_syncer()
            return



@click.command()
@click.option('-sc', '--set-cookie', type=str, help='设置一个 cookie（需要字符串参数）')
@click.option('-uc', '--update-cookie', is_flag=True, help='更新 cookie')
@click.option('-ua', '--update-albums', is_flag=True, help='更新相册列表')
@click.option('-da', '--download-albums', is_flag=True, help='下载已选择的相册')
@click.option('-cd', '--clear-downloads', is_flag=True, help='清空下载记录')
# 纯命令行，无交互
def cli(set_cookie, update_cookie, update_albums, download_albums, clear_downloads):
    """Xiaomi Album Syncer

    一个用于下载小米相册中的照片和视频的工具。
    
    \b
    完整介绍请参阅Github README文件 https://github.com/Coooolfan/XiaomiAlbumSyncer
    在非交互模式下，程序只会执行下述Options选项中第一个被识别到的命令（以下述Options为序），其余命令会被忽略
    或者不传入任何参数，进入交互模式
    """
    if set_cookie:
        task_set_cookie(cookie=set_cookie)
    elif update_cookie:
        asyncio.run(task_refresh_cookie())
    elif update_albums:
        asyncio.run(task_update_album_list())
    elif download_albums:
        asyncio.run(task_download_selected_album())
    elif clear_downloads:
        asyncio.run(task_empty_download_record())

    if not any([set_cookie, update_cookie, update_albums, download_albums, clear_downloads]):
        asyncio.run(icli())

if __name__ == "__main__":
    cli()
