from datetime import datetime
import json
from math import e


class Configer:
    config_file = "config.json"
    default_config = {
        "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0",
        "startDate": "19990101",
        "endDate": datetime.now().strftime("%Y%m%d"),
        "downloadPath": "download",
        "dirName": "name",
        "pageSize": "200",
    }

    @classmethod
    def get(cls, key: str):
        try:
            with open(cls.config_file, "r", encoding="utf-8") as f:
                return json.load(f).get(key, "")
        except FileNotFoundError:
            with open(cls.config_file, "w", encoding="utf-8") as f:
                json.dump(
                    cls.default_config,
                    f,
                    ensure_ascii=False,
                    indent=4,
                )
                return cls.default_config.get(key, "")

        with open(cls.config_file, "r", encoding="utf-8") as f:
            return json.load(f).get(key, "")

    @classmethod
    def set(cls, key: str, value: str):
        try:
            with open(cls.config_file, "r", encoding="utf-8") as f:
                data = json.load(f)
                data[key] = value
            with open(cls.config_file, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=4)
                
        except FileNotFoundError:
            data = cls.default_config
            data[key] = value
            with open(cls.config_file, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=4)
