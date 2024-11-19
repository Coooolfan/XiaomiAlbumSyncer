from ctypes import Array
from datetime import datetime
import asyncio
import json
import sys
from src.browser import BrowserCookie
from src.configer import Configer
from src.manager import Manager
from src.model.album import Album
from src.model.Media import Media
import re

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
            album_obj = Album(
                album_data.get("mediaCount"),
                album_data.get("albumId"),
                album_data.get("name"),
            )
            album_list.append(album_obj)
        return album_list
    else:
        raise Exception(response.text)


async def get_medias(
    album_id: int,
    start_date: datetime,
    end_date: datetime,
) -> list:
    medias = []
    params = {
        "ts": int(datetime.now().timestamp() * 1000),
        "albumId": album_id,
        # "startDate": start_date.strftime("%Y%m%d"),
        # "endDate": end_date.strftime("%Y%m%d"),
        "startDate": 20241019,
        "endDate": 20241019,
        "pageNum": 0,
        "pageSize": 30,
    }
    response = await Manager().download_client.get(
        "https://i.mi.com/gallery/user/galleries", params=params
    )
    if response.status_code == 200:
        galleries_raw_data = response.json().get("data").get("galleries")
        for gallery in galleries_raw_data:
            medias.append(
                Media(
                    gallery.get("fileName"),
                    gallery.get("mimeType"),
                    gallery.get("sha1"),
                    gallery.get("id"),
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
    resp2_json = json.loads(re.search(r'\((.*?)\)', resp2.text).group(1))
    file_download_meta = resp2_json.get("meta")
    file_download_url = resp2_json.get("url")
    form_data = {
        "meta": file_download_meta,
    }
    # 下载文件,保存到本地
    resp3 = await Manager().download_client.post(file_download_url, data=form_data)
    with open(media.filename, "wb") as f:
        f.write(resp3.content)
    print(f"文件{media.filename}下载完成")

def stealCookie() -> str:
    raise Exception("方法不可用！\n请先在浏览器中登录小米云服务, 使用油猴脚本获取cookie并填入config.json后")
    cookie = BrowserCookie.get(
        "edge",
        domains=[
            "i.mi.com",
        ],
    )
    Configer.set("cookie", cookie)
    return cookie


async def main():
    if len(sys.argv) > 1 and sys.argv[1] == "cookie":
        print("获取Cookie中...")
        print(stealCookie())
    else:
        album_list = await get_album_list()
        album = album_list[0]
        medias = await get_medias(album.id, datetime.now(), datetime.now())
        print(medias)
        print("开始下载...")
        await asyncio.gather(*[download_media(media) for media in medias])


if __name__ == "__main__":
    asyncio.run(main())
