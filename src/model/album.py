from peewee import Model, IntegerField, CharField, BooleanField

from src.model.db import db


class Album(Model):
    id = IntegerField(primary_key=True)
    media_count = IntegerField(default=0)
    name = CharField()
    # 当 selected 为 True 时，表示用户选择了该相册，将会在非交互式任务中下载
    selected = BooleanField(default=False)

    class Meta:
        database = db


if __name__ == "__main__":
    db.connect()
    db.create_tables([Album])
    db.close()
