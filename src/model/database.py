from sqlite3 import IntegrityError
from src.model.db import db
from src.model.album import Album
from src.model.media import Media
from playhouse.migrate import SqliteMigrator, migrate
from peewee import IntegerField



def save_media_db(media_list: list):
    with db.atomic():  # 使用事务
        for media in media_list:
            if Media.get_or_none(Media.id == media.id):
                Media.update(
                    filename=media.filename,
                    mime_type=media.mime_type,
                    media_type=media.media_type,
                    sha1=media.sha1,
                    date_modified=media.date_modified
                ).where(Media.id == media.id).execute()
            else:
                try:
                    Media.create(
                        id=media.id,
                        album_id=media.album_id,
                        filename=media.filename,
                        mime_type=media.mime_type,
                        media_type=media.media_type,
                        sha1=media.sha1,
                        date_modified=media.date_modified,
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
    migrator = SqliteMigrator(db)
    try:
        db.connect()
        db.create_tables([Media, Album], safe=True)  # safe=True 确保表不存在时才创建

        # 检查字段是否存在
        if not column_exists('media', 'date_modified'):
            print("数据库表 media 缺少字段 date_modified, 正在添加...")
            with db.atomic():
                migrate(
                    migrator.add_column('media', 'date_modified', IntegerField(default=0)),
                )
    except Exception as e:
        print(f"创建数据库表时出错: {e}")
    finally:
        db.close()

def column_exists(table_name, column_name):
    cursor = db.execute_sql(f"PRAGMA table_info({table_name});")
    columns = [row[1] for row in cursor.fetchall()]
    return column_name in columns