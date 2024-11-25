from peewee import Model, IntegerField, CharField, BooleanField

from src.model.db import db


class Media(Model):
    id = IntegerField(primary_key=True)
    album_id = IntegerField()
    filename = CharField()
    mime_type = CharField()
    media_type = CharField()
    sha1 = CharField()
    downloaded = BooleanField(default=False)

    class Meta:
        database = db


if __name__ == "__main__":
    db.connect()
    db.create_tables([Media])
    db.close()
