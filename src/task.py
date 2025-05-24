from datetime import datetime
import os
from InquirerPy.validator import PathValidator

from tqdm import tqdm

from src.model.media import Media
from src.model.database import init_db, save_album_db, save_media_db
from src.api import download_and_save_media, fill_exif, get_album_list, get_media_list
from src.api import refresh_cookie as refresh_cookie_api
from src.configer import Configer
from src.PersistentCookies import PersistentCookies
from src.model.album import Album
from src.manager import Manager
from InquirerPy import inquirer
import nest_asyncio

nest_asyncio.apply()


def steal_cookie() -> str:
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
        print("获取此相册第", pageNum, "页媒体文件...")
        media_list = await get_media_list(
            album_id, pageNum, Configer.get("startDate"), Configer.get("endDate")
        )
        if len(media_list) == 0:
            break
        medias.extend(media_list)
        pageNum += 1
    return medias

def config_selected_album():
    album_list = Album.select().execute()
    if len(album_list) == 0:
        print("请先更新相册列表！")
        return
    album_name_list = [album.name for album in album_list]
    album_name = inquirer.checkbox(
        message="选择要下载的相册(使用空格切换，回车确认)",
        choices=album_name_list,
    ).execute()
    Album.update(selected=False).execute()
    album_selected_list = []
    for album in album_list:
        if album.name in album_name:
            album_selected_list.append(album)
    for album in album_selected_list:
        album.selected = True
        album.save()
    print("已选", len(album_selected_list), "个相册")


def set_cookie(cookie: str = None):
    if cookie is None:
        cookie = input("请输入cookie:\n")
    PersistentCookies.parse_cookie_string(cookie, Manager().download_client.cookies)
    # Configer.set("cookie", cookie)
    print("更新cookie成功！")


async def refresh_cookie():
    await refresh_cookie_api()


def modify_config():
    config_item = inquirer.select(
        message="可编辑配置项",
        choices=["下载路径", "相册文件夹命名方式", "是否填充照片Exif", "是否下载视频", "是否填充视频Exif"],
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
    elif config_item == "是否填充照片Exif":
        fill_exif = inquirer.select(
            message="是否填充照片Exif",
            choices=[
                "是（程序仅会在目标文件与时间相关的标签为空时，将空值填充为文件上传到小米云服务的时间。对于视频文件，确保已于config.json中配置exiftool路径）",
                "否",
            ],
        ).execute()
        if fill_exif == "否":
            fill_exif = "false"
        else:
            fill_exif = "true"
        Configer.set("fillExif", fill_exif)
    elif config_item == "是否下载视频":
        download_video = inquirer.select(
            default="否（默认）",
            message="是否下载视频, 默认不下载",
            choices=[
                "是",
                "否（默认）",
            ],
        ).execute()
        if download_video == "是":
            download_video = "true"
        else:
            download_video = "false"
        Configer.set("downloadVideo", download_video)
    elif config_item == "是否填充视频Exif":
        fill_video_exif = inquirer.select(
            message="是否填充视频Exif",
            choices=["是", "否（默认）"],
        ).execute() 
        if fill_video_exif == "是":
            fill_video_exif = "true"
            print("填充视频Exif 功能依赖exiftool，程序默认使用`exiftool`作为exiftool路径，请确保exiftool已安装且在环境变量中。或者在config.json中配置exiftool路径")
        else:
            fill_video_exif = "false"
        Configer.set("fillVideoExif", fill_video_exif)


async def update_album_list():
    album_list = await get_album_list()
    save_album_db(album_list)
    print("更新相册列表成功！")


async def download_selected_album():
    album_list = Album.select().where(Album.selected).execute()
    if len(album_list) == 0:
        print("没有选择任何相册！")
        return
    print("开始下载已选择的相册")
    for album in album_list:
        await download_single_album(album)


async def download_single_album(album: Album):
    print("---------------------------------")
    print("更新相册", album.name, "...")
    all_medias = await get_all_media_from_album(album.id)
    undownloaded_medias = save_media_db(all_medias)
    print("开始下载相册", album.name, "...")
    print("准备下载", len(undownloaded_medias), "个文件")
    print("---------------------------------")
    # 计时
    start_time = datetime.now()
    # 对下载速度应该没什么需求吧，就不用异步了
    for media in tqdm(undownloaded_medias, desc="下载进度", unit="file"):
        await download_and_save_media(media)
    end_time = datetime.now()
    print("---------------------------------")
    print("下载完成！")
    print("耗时：", end_time - start_time)
    print("---------------------------------")


async def select_and_download_single_album():
    album_list = Album.select().execute()
    if len(album_list) == 0:
        print("请先更新相册列表！")
        return
    album_name_list = [album.name for album in album_list]
    album_name = inquirer.select(
        message="选择要下载的相册",
        choices=album_name_list,
    ).execute()
    album = Album.get(Album.name == album_name)
    await download_single_album(album)


async def empty_download_record():
    # 覆写所有Media的downloaded字段
    Media.update(downloaded=False).execute()


def init_syncer():
    Configer.init_config()
    init_db()


def fill_all_exif():
    medias = Media.select().execute()
    print("开始填充Exif信息...")
    for media in tqdm(medias, desc="填充Exif信息..."):
        download_path = Configer.get("downloadPath")
        if Configer.get("dirName") == "name":
            download_path = (
                download_path + "/" + Album.get(Album.id == media.album_id).name + "/"
            )
        else:
            download_path = download_path + "/" + str(media.album_id) + "/"
        os.makedirs(download_path, exist_ok=True)
        target_file_path = download_path + media.filename
        fill_exif(media, target_file_path)


async def exit_syncer():
    await Manager().download_client.aclose()
