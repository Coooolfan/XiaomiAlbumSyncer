import json
from httpx import Cookies

class PersistentCookies:
    @staticmethod
    def parse_cookie_string(cookie_string: str, client_cookies: Cookies):
        """
        将用户输入的 Cookie 字符串解析并插入到 Cookies 对象中。
        :param cookie_string: 用户输入的 Cookie 字符串。
        :param client_cookies: httpx.Cookies 对象。
        """
        cookie_pairs = cookie_string.split(";")
        for pair in cookie_pairs:
            if "=" in pair:
                key, value = pair.split("=", 1)  # 分离键和值
                client_cookies.set(key.strip(), value.strip())

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
        
        # 此方法只在设置 cookie 后第一次保存时有意义
        # 手动设置的 cookie 无domain和path, 在新cookie获取后不会被覆盖，需要手动以name去重
        unique_cookies = {}
        for cookie in cookies_dict:  # 遍历，确保保留排序靠后的
            unique_cookies[cookie["name"]] = cookie
        cookies_dict = list(unique_cookies.values())  # 转回列表，恢复顺序

        # 写入到文件
        with open(file_path, "w", encoding="utf-8") as f:
            json.dump(cookies_dict, f, indent=4)

    @staticmethod
    def load_from_file(client_cookies: Cookies, file_path: str):
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                cookies_dict = json.load(f)
                client_cookies.clear()
                for cookie in cookies_dict:
                    client_cookies.set(
                        cookie["name"],
                        cookie["value"],
                        domain=cookie["domain"],
                        path=cookie["path"],
                    )
        except json.JSONDecodeError:
            print(f"Cookie 文件 {file_path} 解析失败。程序将以空 Cookie 启动。\n如果您在“设置cookie”命令过程中看到了此提示，请无视\n否则，请执行“设置cookie”命令以重新设置 Cookie。")
            with open(file_path, "w", encoding="utf-8") as f:
                f.write("[]")
            client_cookies.clear()
