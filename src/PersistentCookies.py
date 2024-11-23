import json
from httpx import Cookies

class PersistentCookies:
    @staticmethod
    def save_to_file(client_cookies: Cookies, file_path: str):
        # 提取 Cookie 信息并序列化为 JSON
        cookies_dict = [
            {
                "name": cookie.name,
                "value": cookie.value,
                "domain": cookie.domain,
                "path": cookie.path,
            }
            for cookie in client_cookies.jar
        ]
        with open(file_path, "w", encoding="utf-8") as f:
            json.dump(cookies_dict, f, indent=4)

    @staticmethod
    def load_from_file(client_cookies: Cookies, file_path: str):
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                cookies_dict = json.load(f)
                for cookie in cookies_dict:
                    client_cookies.set(
                        cookie["name"],
                        cookie["value"],
                        domain=cookie["domain"],
                        path=cookie["path"],
                    )
        except FileNotFoundError:
            print(f"Cookie file {file_path} not found. Starting with an empty jar.")
