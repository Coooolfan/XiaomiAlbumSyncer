import json


class Configer:
    config_file = 'config.json'
    
    @classmethod
    def get(cls, key: str):
        with open(cls.config_file, 'r', encoding='utf-8') as f:
            return json.load(f).get(key, '')
        
    @classmethod
    def set(cls, key: str, value: str):
        with open(cls.config_file, 'r', encoding='utf-8') as f:
            data = json.load(f)
            data[key] = value
        with open(cls.config_file, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=4)
    
    @classmethod
    def remove(cls, key: str):
        with open(cls.config_file, 'r', encoding='utf-8') as f:
            data = json.load(f)
            data.pop(key, None)
        with open(cls.config_file, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=4)
            
    @classmethod
    def get_all(cls):
        with open(cls.config_file, 'r', encoding='utf-8') as f:
            return json.load(f)