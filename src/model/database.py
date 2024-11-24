from sqlite3 import IntegrityError
from src.model.db import db
from src.model.Album import Album
from src.model.Media import Media


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


def save_album_db(album_list: list):
    with db.atomic():  # 使用事务
        for album in album_list:
            if Album.get_or_none(Album.id == album.id):
                Album.update(
                    media_count=album.media_count,
                    name=album.name,
                ).where(Album.id == album.id).execute()
            else:
                try:
                    Album.create(
                        id=album.id,
                        media_count=album.media_count,
                        name=album.name,
                    )
                except IntegrityError:
                    # 如果记录已存在，则忽略错误
                    pass


def init_db():
    try:
        db.connect()
        db.create_tables([Media, Album], safe=True)  # safe=True 确保表不存在时才创建
    except Exception as e:
        print(f"创建数据库表时出错: {e}")
    finally:
        db.close()