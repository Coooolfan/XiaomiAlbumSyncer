from xmlrpc.client import Boolean
from peewee import Model, IntegerField, CharField,SqliteDatabase,BooleanField
from .database import db

class Album(Model):
    id = IntegerField(primary_key=True)
    media_count = IntegerField(default=0)
    name = CharField()
    
    class Meta:
        database = db
        
if(__name__=="__main__"):
    db.connect()
    db.create_tables([Album])
    db.close()