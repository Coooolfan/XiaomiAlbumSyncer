import asyncio

from InquirerPy import inquirer
import nest_asyncio

from src.task import (
    download_single_album,
    modify_config,
    update_album_list,
    update_cookie,
)

nest_asyncio.apply()


async def main():
    while True:
        task = inquirer.select(
            message="欢迎回来，准备干些什么捏",
            choices=["更新cookie", "修改配置", "更新相册列表", "下载单个相册", "退出"],
        ).execute()

        if task == "更新cookie":
            update_cookie()
        elif task == "修改配置":
            modify_config()
        elif task == "更新相册列表":
            await update_album_list()
        elif task == "下载单个相册":
            await download_single_album()
        elif task == "退出":
            return


if __name__ == "__main__":
    asyncio.run(main())
