# https://github.com/JoeanAmier/XHS-Downloader/blob/54638549063a54d850340f03473950cb388364c4/source/expansion/browser.py

from contextlib import suppress

from rookiepy import (
    arc,
    brave,
    chrome,
    chromium,
    edge,
    firefox,
    librewolf,
    opera,
    # opera_gx,
    # safari,
    vivaldi,
)

__all__ = ["BrowserCookie"]


class BrowserCookie:
    SUPPORT_BROWSER = {
        "arc": arc,
        "chrome": chrome,
        "chromium": chromium,
        "opera": opera,
        # "opera_gx": opera_gx,
        "brave": brave,
        "edge": edge,
        "vivaldi": vivaldi,
        "firefox": firefox,
        "librewolf": librewolf,
        # "safari": safari,
    }

    @classmethod
    def get(cls, browser: str | int, domains: list[str]) -> str:
        if not (browser := cls.__browser_object(browser)):
            print("浏览器名称或序号输入错误！")
            return ""
        try:
            cookies = browser(domains=domains)
            return "; ".join(f"{i["name"]}={i["value"]}" for i in cookies)
        except RuntimeError as e:
            if e.__str__().__contains__("as admin"):
                print("获取Cookie失败! 请以管理员身份运行程序！")
            else:
                print("获取Cookie失败！\n", e)
        return ""

    @classmethod
    def __browser_object(cls, browser: str | int):
        with suppress(ValueError):
            browser = int(browser) - 1
        if isinstance(browser, int):
            try:
                return list(cls.SUPPORT_BROWSER.values())[browser - 1]
            except IndexError:
                return None
        if isinstance(browser, str):
            try:
                return cls.SUPPORT_BROWSER[browser.lower()]
            except KeyError:
                return None
        raise TypeError
