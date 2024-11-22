from datetime import datetime
import os
from InquirerPy.validator import PathValidator

from tqdm import tqdm
from src.model.database import save_album_db, save_media_db
from src.api import download_and_save_media, get_album_list, get_media_list
from src.configer import Configer
from src.model.Album import Album
from InquirerPy import inquirer
import nest_asyncio

nest_asyncio.apply()


def stealCookie() -> str:
    raise Exception(
        "方法不可用！\n请先在浏览器中登录小米云服务, 使用油猴脚本获取cookie并填入config.json后"
    )
    cookie = BrowserCookie.get(
        "edge",
        domains=[
            "i.mi.com",
        ],
    )
    Configer.set("cookie", cookie)
    return cookie


async def get_all_media_from_album(album_id: int):
    medias = []
    pageNum = 0
    while True:
        media_list = await get_media_list(
            album_id, pageNum, Configer.get("startDate"), Configer.get("endDate")
        )
        if len(media_list) == 0:
            break
        medias.extend(media_list)
        pageNum += 1
    return medias


def update_cookie():
    cookie = input("请输入cookie:\n")
    Configer.set("cookie", cookie)
    print("更新cookie成功！")


def modify_config():

    config_item = inquirer.select(
        message="可编辑配置项",
        choices=["下载路径", "相册文件夹命名方式"],
    ).execute()

    if config_item == "下载路径":
        home_path = os.getcwd()  # 获取当前工作目录
        download_path = inquirer.filepath(
            message="输入下载路径",
            default=home_path,
            validate=PathValidator(is_dir=True, message="这不是一个目录！"),
            only_directories=True,
        ).execute()
        Configer.set("downloadPath", download_path)
    elif config_item == "相册文件夹命名方式":
        folder_name = inquirer.select(
            message="选择相册文件夹命名方式",
            choices=["相册名", "相册ID"],
        ).execute()
        if folder_name == "相册名":
            folder_name = "name"
        else:
            folder_name = "id"
        Configer.set("dirName", folder_name)


async def update_album_list():
    album_list = await get_album_list()
    save_album_db(album_list)
    print("更新相册列表成功！")


async def download_single_album():
    album_list = Album.select().execute()
    album_name_list = [album.name for album in album_list]
    album_name = inquirer.select(
        message="选择要下载的相册",
        choices=album_name_list,
    ).execute()
    album = Album.get(Album.name == album_name)
    medias = await get_all_media_from_album(album.id)
    save_media_db(medias)
    print("---------------------------------")
    print("开始下载...")
    print("共计", len(medias), "个文件")
    print("---------------------------------")
    # 计时
    start_time = datetime.now()
    # 对下载速度应该没什么需求吧，就不用异步了
    for media in tqdm(medias, desc="下载进度", unit="file"):
        await download_and_save_media(media)
    end_time = datetime.now()
    print("---------------------------------")
    print("下载完成！")
    print("耗时：", end_time - start_time)
    print("---------------------------------")
