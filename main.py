from ctypes import Array
from datetime import datetime
import asyncio
import json
import os
import sys
from turtle import down
from InquirerPy.validator import PathValidator

from tqdm import tqdm
from src.configer import Configer
from src.manager import Manager
from src.model.Album import Album
from src.model.Media import Media
import re
from src.static import ALBUMID_MAP
from peewee import IntegrityError
from src.model.database import db
from InquirerPy import inquirer


async def get_album_list() -> list:
    album_raw_list: Array = []
    album_list = []
    url = "https://i.mi.com/gallery/user/album/list"
    params = {
        "ts": int(datetime.now().timestamp() * 1000),
        "pageNum": 0,
        "pageSize": 100,
        "isShared": "false",
        "numOfThumbnails": 1,
    }
    response = await Manager().download_client.get(url, params=params)
    if response.status_code == 200:
        album_raw_list = response.json().get("data").get("albums")
        for album_data in album_raw_list:

            album_id = int(album_data.get("albumId"))

            # 私密相册需要二次验证，无法实现
            if album_id == 1000:
                continue

            if album_id in ALBUMID_MAP.keys():
                album_name = ALBUMID_MAP[album_id]
            else:
                album_name = album_data.get("name")

            album_obj = Album(
                id=album_id,
                media_count=int(album_data.get("mediaCount")),
                name=album_name,
            )
            # if(album_obj.save()==0):
            #     raise Exception("保存失败")
            album_list.append(album_obj)

        return album_list
    else:
        raise Exception(response.text)


async def get_medias(
    album_id: int,
    pageNum: int,
    start_date: str,
    end_date: str,
) -> list:
    medias = []
    params = {
        "ts": int(datetime.now().timestamp() * 1000),
        "albumId": album_id,
        "startDate": start_date,
        "endDate": end_date,
        "pageNum": pageNum,
        "pageSize": 50,
    }
    response = await Manager().download_client.get(
        "https://i.mi.com/gallery/user/galleries", params=params
    )
    if response.status_code == 200:
        galleries_raw_data = response.json().get("data").get("galleries")
        if galleries_raw_data is None:
            return medias
        for gallery in galleries_raw_data:
            medias.append(
                Media(
                    id=gallery.get("id"),
                    album_id=album_id,
                    filename=gallery.get("fileName"),
                    mime_type=gallery.get("mimeType"),
                    media_type=gallery.get("type"),
                    sha1=gallery.get("sha1"),
                    downloaded=False,
                )
            )
        return medias
    else:
        raise Exception(response.text)


async def download_media(media: Media):
    if "image" not in media.mime_type:
        print(f"文件{media.filename}不是图片,跳过下载")
        return
    resp1 = await Manager().download_client.get(
        "https://i.mi.com/gallery/storage",
        params={
            "id": media.id,
            "ts": int(datetime.now().timestamp() * 1000),
        },
    )
    url1 = resp1.json().get("data").get("url")
    resp2 = await Manager().download_client.get(url1)
    # 取出字符串()中的内容
    resp2_json = json.loads(re.search(r"\((.*?)\)", resp2.text).group(1))
    file_download_meta = resp2_json.get("meta")
    file_download_url = resp2_json.get("url")
    form_data = {
        "meta": file_download_meta,
    }
    # 下载文件,保存到本地
    resp3 = await Manager().download_client.post(file_download_url, data=form_data)

    os.makedirs("media", exist_ok=True)
    with open("media/" + media.filename, "wb") as f:
        f.write(resp3.content)
    print(f"文件{"media/" + media.filename}下载完成")


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


def save_album_db(album_list: list):
    for album in album_list:
        # 存在更新、不存在保存
        if Album.get_or_none(Album.id == album.id):
            Album.update(
                media_count=album.media_count,
                name=album.name,
            ).where(Album.id == album.id).execute()
        else:
            Album.create(
                id=album.id,
                media_count=album.media_count,
                name=album.name,
            )


def save_media_db(media_list: list):
    with db.atomic():  # 使用事务
        for media in media_list:
            if not Media.get_or_none(Media.id == media.id):
                try:
                    Media.create(
                        id=media.id,
                        album_id=media.album_id,
                        filename=media.filename,
                        mime_type=media.mime_type,
                        media_type=media.media_type,
                        sha1=media.sha1,
                        downloaded=media.downloaded,
                    )
                except IntegrityError:
                    # 如果记录已存在，则忽略错误
                    pass


async def get_all_media_from_album(album_id: int):
    medias = []
    pageNum = 0
    while True:
        media_list = await get_medias(
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
        if(folder_name == "相册名"):
            folder_name = "name"
        else:
            folder_name = "id"
        Configer.set("dirName", folder_name)
        
def main():
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
            print("开始更新相册列表...")
        elif task == "下载单个相册":
            print("开始下载单个相册...")
        elif task == "退出":
            return
    # medias =await get_all_media_from_album(12656130040726944)
    # save_media_db(medias)

    # medias = Media.select().execute()
    # print(len(medias))
    # for media in tqdm(medias, desc="Downloading Media", unit="file"):
    #     await download_media(media)

    # album_list = await get_album_list()
    # save_album_db(album_list)
    # medias = await get_medias(album.id, datetime.now(), datetime.now())
    # print(medias)
    # print("开始下载...")
    # await asyncio.gather(*[download_media(media) for media in medias])


if __name__ == "__main__":
    main()
