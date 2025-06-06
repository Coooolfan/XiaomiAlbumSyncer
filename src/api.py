import json
import os
import asyncio
import re
from datetime import datetime
import traceback

from src.exiftool import fill_exif
from src.configer import Configer
from src.manager import Manager
from src.model.album import Album
from src.model.media import Media
from src.static import ALBUMID_MAP

last_cookie_refresh_time = None
lock = asyncio.Lock()


async def get_album_list() -> list:
    await check_and_refresh_cookie()
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
            album_list.append(album_obj)

        return album_list
    else:
        raise Exception(response.text)


async def get_media_list(
    album_id: int,
    pageNum: int,
    start_date: str,
    end_date: str,
) -> list:
    await check_and_refresh_cookie()
    medias = []
    params = {
        "ts": int(datetime.now().timestamp() * 1000),
        "albumId": album_id,
        "startDate": start_date,
        "endDate": end_date,
        "pageNum": pageNum,
        "pageSize": Configer.get("pageSize"),
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
                    date_modified=gallery.get("dateModified"),
                    downloaded=False,
                )
            )
        return medias
    else:
        raise Exception(response.text)


async def download_and_save_media(media: Media):
    # 传入的 media 对象是从网络获取的，需要检索数据库获取完整的 media 对象
    # 主要是 downloaded 值需要从数据库获取
    await check_and_refresh_cookie()
    media = Media.get(Media.id == media.id)
    try:
        if "video" in media.mime_type:
            if Configer.get("downloadVideo") != "true":
                print(f"文件{media.filename}为视频, 未开启下载视频选项, 跳过下载")
                return
            
        if media.downloaded:
            # print(f"文件{media.filename}已下载,跳过下载")
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
        download_path = Configer.get("downloadPath")
        if Configer.get("dirName") == "name":
            download_path = (
                download_path + "/" + Album.get(Album.id == media.album_id).name + "/"
            )
        else:
            download_path = download_path + "/" + str(media.album_id) + "/"
        os.makedirs(download_path, exist_ok=True)
        target_file_path = download_path + media.filename
        with open(target_file_path, "wb") as f:
            f.write(resp3.content)
        media.downloaded = True
        media.save()
        fill_exif(media, target_file_path)
        # print(f"文件{"media/" + media.filename}下载完成")
    except Exception as e:
        print(f"文件{media.filename}下载失败,原因:{e}\n ")
        traceback.print_exc()


async def check_and_refresh_cookie():
    global last_cookie_refresh_time
    async with lock:
        if (
            last_cookie_refresh_time is None
            or (datetime.now() - last_cookie_refresh_time).total_seconds() > 180
        ):
            await refresh_cookie()
            last_cookie_refresh_time = datetime.now()


async def refresh_cookie():
    resp = await Manager().download_client.get(
        "https://i.mi.com/status/lite/setting?type=AutoRenewal&inactiveTime=10"
    )
    ok = resp.json().get("result") == "ok"
    if (not ok) or (resp.status_code != 200):
        raise Exception("Cookie刷新失败，请重新“设置Cookie”后重试")
    print("Cookie刷新成功")
    global last_cookie_refresh_time
    last_cookie_refresh_time = datetime.now()
