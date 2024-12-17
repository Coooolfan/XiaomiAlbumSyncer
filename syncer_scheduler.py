import os
import time
import logging
from datetime import datetime
import subprocess

# 配置日志文件
LOG_FILE = "xiaomi_album_syncer.log"
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.FileHandler(LOG_FILE, mode='a'),
        logging.StreamHandler()  # 可选，控制台输出
    ]
)

def log_message(message):
    """打印带时间戳的消息"""
    logging.info(message)

def run_command(command):
    """运行 shell 命令并记录输出"""
    try:
        log_message(f"运行命令: {command}")
        result = subprocess.run(
            command, shell=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE
        )
        # 记录命令输出
        if result.stdout:
            logging.info(f"命令输出: {result.stdout.strip()}")
        if result.stderr:
            logging.error(f"命令错误: {result.stderr.strip()}")
    except Exception as e:
        logging.error(f"命令执行失败: {e}")

def run_update_command():
    """运行 './XiaomiAlbumSyncer-linux -uc' 命令"""
    run_command('./XiaomiAlbumSyncer-linux -uc')

def run_download_command():
    """运行 './XiaomiAlbumSyncer-linux -da' 命令"""
    run_command('./XiaomiAlbumSyncer-linux -da')

if __name__ == "__main__":
    log_message("任务启动成功。")
    last_update_time = 0  # 上次运行更新命令的时间
    last_download_time = 0  # 上次运行下载命令的时间

    while True:
        current_time = time.time()

        # 每隔 1 分钟运行更新命令
        if current_time - last_update_time >= 60:  # 60 秒 = 1 分钟
            run_update_command()
            last_update_time = current_time

        # 每隔 30 分钟运行下载命令
        if current_time - last_download_time >= 1800:  # 1800 秒 = 30 分钟
            run_download_command()
            last_download_time = current_time

        # 每秒检查一次
        time.sleep(1)