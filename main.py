import asyncio
from datetime import datetime

import nest_asyncio
from InquirerPy import inquirer

from src.configer import Configer
from src.task import (modify_config, update_album_list, set_cookie, config_selected_album, download_selected_album,
                      empty_download_record, select_and_download_single_album, exit_syncer, refresh_cookie)

nest_asyncio.apply()


async def main():
    Configer.set("endDate", datetime.now().strftime("%Y%m%d"))
    while True:
        task = inquirer.select(
            message="欢迎回来，准备干些什么捏",
            choices=["设置cookie", "更新cookie", "更新相册列表", "选择要下载的相册", "下载已选择的相册", "修改配置",
                     "下载单个相册",
                     "清空下载记录", "退出"],
        ).execute()

        if task == "设置cookie":
            set_cookie()
        elif task == "更新cookie":
            await refresh_cookie()
        elif task == "更新相册列表":
            await update_album_list()
        elif task == "选择要下载的相册":
            config_selected_album()
        elif task == "下载已选择的相册":
            check = inquirer.confirm(
                message="确认开始下载已选择的相册吗？\n此操作可能会花费大量时间，如果需要测试配置是否正确请选择‘下载单个相册’",
                default=True).execute()
            if check:
                await download_selected_album()
        elif task == "修改配置":
            modify_config()
        elif task == "下载单个相册":
            await select_and_download_single_album()
        elif task == "清空下载记录":
            check = inquirer.confirm(
                message="确认清空下载记录吗？\n清空后的第一次下载会下载所有媒体文件并复盖原文件",
                default=True).execute()
            if check:
                await empty_download_record()
        elif task == "退出":
            await exit_syncer()
            return


if __name__ == "__main__":
    asyncio.run(main())
