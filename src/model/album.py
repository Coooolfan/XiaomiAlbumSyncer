from src.static import ALBUMID_MAP


class Album:
    def __init__(self, media_cout, id, name):

        self.id = id
        self.media_cout = media_cout

        if int(id) in ALBUMID_MAP.keys():
            self.name = ALBUMID_MAP.get(int(id))
        else:
            self.name = name

    def __str__(self):
        return f"相册名称:{self.name};照片数量:{self.media_cout}"

    def __repr__(self):
        return f"相册名称:{self.name},照片数量:{self.media_cout}"

    def __eq__(self, other):
        return self.id == other.id

    def __hash__(self):
        return hash(self.id)
