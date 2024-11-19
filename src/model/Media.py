class Media:
    def __init__(self, filename, mime_type, sha1, id):

        self.id = id
        self.filename = filename
        self.mime_type = mime_type
        self.sha1 = sha1

    def __str__(self):
        return f"照片文件名:{self.filename};照片ID:{self.id}"

    def __repr__(self):
        return f"照片文件名:{self.filename};照片ID:{self.id}"

    def __eq__(self, other):
        return self.sha1 == other.sha1

    def __hash__(self):
        return self.sha1
