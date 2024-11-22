from httpx import AsyncClient, Request, Response
from threading import Lock
from src.configer import Configer


class Manager:
    _instance = None
    _lock = Lock()

    def __new__(cls, *args, **kwargs):
        if not cls._instance:
            with cls._lock:
                if not cls._instance:
                    cls._instance = super(Manager, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        if not hasattr(self, "initialized"):  # 防止重复初始化
            self.initialized = True
            self.blank_headers = {
                "User-Agent": Configer.get("userAgent"),
                "Cookie": Configer.get("cookie"),
            }
            self.timeout = 10.0  # 默认超时时间

            # 定义请求拦截器
            async def on_request(request: Request):
                pass
                # print(f"请求 URL: {request.url}")
                # print(f"请求头: {request.headers}")
                # print(f"请求体: {request.content}")

            # 定义响应拦截器
            async def on_response(response: Response):
                pass
                # print(f"响应状态码: {response.status_code}")
                # print(f"响应头: {response.headers}")
                # print(f"响应体: {response.cookies}")

            self.download_client = AsyncClient(
                headers=self.blank_headers,
                timeout=self.timeout,
                verify=False,
                follow_redirects=True,
                event_hooks={
                    "request": [on_request],
                    "response": [on_response],
                },
            )
