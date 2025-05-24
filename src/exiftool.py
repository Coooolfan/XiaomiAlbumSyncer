from datetime import datetime
import subprocess
import json

import piexif

from src.configer import Configer
from src.model.media import Media
from src.static import VIDEO_DATA_TAG

def fill_exif(media: Media, file_path: str):
    if Configer.get("fillExif") == "false":
        return
    if media.date_modified == 0:
        return
    
    if media.media_type == "image":
        fill_image_exif(media, file_path)
    elif media.media_type == "video":
        fill_video_exif(media, file_path)
    else:
        raise ValueError(f"媒体类型{media.media_type}未知")

def fill_image_exif(media: Media, file_path: str):
    if not any(ext in media.mime_type for ext in ["jpeg", "jpg", "tif", "tiff"]):
        return
  
    try:
        new_time_str = None
        need_fill = False
        exif_data = piexif.load(file_path)
        existing_DateTime = exif_data["0th"].get(piexif.ImageIFD.DateTime)
        existing_DateTimeDigitized = exif_data["Exif"].get(
            piexif.ExifIFD.DateTimeDigitized
        )
        existing_DateTimeOriginal = exif_data["Exif"].get(
            piexif.ExifIFD.DateTimeOriginal
        )
        # 当原图的Exif值存在时，优先使用原图的数据
        if (existing_DateTime is not None) and (new_time_str is None):
            new_time_str = existing_DateTime
        if (existing_DateTimeDigitized is not None) and (new_time_str is None):
            new_time_str = existing_DateTimeDigitized
        if (existing_DateTimeOriginal is not None) and (new_time_str is None):
            new_time_str = existing_DateTimeOriginal
        if new_time_str is None:
            new_time_str = datetime.fromtimestamp(media.date_modified / 1000).strftime(
                "%Y:%m:%d %H:%M:%S"
            )

        # 只填充空值
        if existing_DateTime is None:
            exif_data["0th"][piexif.ImageIFD.DateTime] = new_time_str
            need_fill = True
        if existing_DateTimeDigitized is None:
            exif_data["Exif"][piexif.ExifIFD.DateTimeDigitized] = new_time_str
            need_fill = True
        if existing_DateTimeOriginal is None:
            exif_data["Exif"][piexif.ExifIFD.DateTimeOriginal] = new_time_str
            need_fill = True
            
        # 只在发生编辑的时候保存
        if need_fill:
            exif_bytes = piexif.dump(exif_data)
            piexif.insert(exif_bytes, file_path)
            print(f"文件{media.filename}填充Exif成功")

    except Exception as e:
        print(
            f"文件{media.filename}填充Exif失败, 原因:{e}\n这一般是由于兼容性问题导致的, 如果目标文件存在, 此文件的Exif信息不会被修改, 否则什么都不会发生"
        )

def fill_video_exif(media: Media, file_path: str):
    """
    填充视频的Exif信息
    只填充值为"0000:00:00 00:00:00"的字段
    """
    if Configer.get("fillVideoExif") != "true":
        return
    if media.date_modified == 0:
        return
    
    # 先查询文件已有的exif信息
    try:
        existing_exif = query_video_exif(file_path)
        
        # 检查是否所有标签都已有有效值（不为"0000:00:00 00:00:00"）
        all_valid = True
        for tag in VIDEO_DATA_TAG:
            tag_value = existing_exif.get(tag)
            if tag_value is None or tag_value == "0000:00:00 00:00:00":
                all_valid = False
                break
        
        # 如果所有标签都有有效值，直接返回
        if all_valid:
            return
        
        # 使用media.date_modified生成时间字符串，格式为YYYY:MM:DD HH:MM:SS
        date_str = datetime.fromtimestamp(media.date_modified / 1000).strftime("%Y:%m:%d %H:%M:%S")
        
        # 构建修改命令，只填充无效值
        modifications = {}
        for tag in VIDEO_DATA_TAG:
            tag_value = existing_exif.get(tag)
            if tag_value is None or tag_value == "0000:00:00 00:00:00":
                modifications[tag] = date_str
        
        # 如果没有需要修改的项，直接返回
        if not modifications:
            return
        
        # 调用修改函数
        modify_video_exif(file_path, modifications)
        print(f"文件{media.filename}填充视频Exif成功")
    except Exception as e:
        print(f"文件{media.filename}填充视频Exif失败, 原因:{e}")

def query_video_exif(file_path: str):
    """
    查询视频的Exif信息，只返回在VIDEO_DATA_TAG中定义的标签
    
    Args:
        file_path: 视频文件路径
        
    Returns:
        dict: 包含视频Exif信息的字典，只包含VIDEO_DATA_TAG中定义的标签
    """
    try:
        # 使用通用函数执行命令
        stdout, _ = run_exiftool_command(["-j", "-G", file_path])
        
        # 解析JSON输出
        full_exif_data = json.loads(stdout)
        if full_exif_data and len(full_exif_data) > 0:
            full_exif = full_exif_data[0]  # exiftool返回的是一个列表，每个文件一个元素
            
            # 只保留VIDEO_DATA_TAG中定义的标签
            filtered_exif = {}
            for tag in VIDEO_DATA_TAG:
                if "QuickTime:"+tag in full_exif:
                    filtered_exif[tag] = full_exif["QuickTime:"+tag]
            
            return filtered_exif
        return {}
    except Exception as e:
        print(f"查询视频Exif信息时发生错误: {e}")
        return {}

def modify_video_exif(file_path: str, modifications: dict):
    """
    修改视频的Exif信息，只修改在VIDEO_DATA_TAG中定义的标签
    
    Args:
        file_path: 视频文件路径
        modifications: 要修改的Exif标签和值的字典，例如 {"CreateDate": "2023:01:01 12:00:00"}
                      只有在VIDEO_DATA_TAG中定义的标签才会被处理
    """
    try:
        # 过滤掉不在VIDEO_DATA_TAG中的标签
        filtered_modifications = {}
        for tag, value in modifications.items():
            if tag in VIDEO_DATA_TAG:
                filtered_modifications[tag] = value
        
        # 如果没有有效的修改项，直接返回
        if not filtered_modifications:
            return False
        
        # 构建命令参数
        args = []
        
        # 添加每个修改项
        for tag, value in filtered_modifications.items():
            args.append(f"-{tag}={value}")
        
        # 添加文件路径和覆盖原文件的选项
        args.append("-overwrite_original")
        args.append(file_path)
        
        # 使用通用函数执行命令
        _, stderr = run_exiftool_command(args)
        
        if stderr:
            print(f"修改视频Exif信息警告: {stderr}")
        
        return True
    except Exception as e:
        print(f"修改视频Exif信息时发生错误: {e}")
        raise

def run_exiftool_command(args: list[str]):
    """
    执行exiftool命令
    
    Args:
        args: exiftool命令参数列表，不包含'exiftool'本身
        
    Returns:
        tuple: (stdout, stderr) - 命令的标准输出和标准错误
        
    Raises:
        subprocess.CalledProcessError: 如果命令执行失败
    """

    exiftool_path = Configer.get("exiftoolPath")
    if(exiftool_path == ""):
        raise ValueError("exiftool路径未配置")  
    
    try:
        # 构建完整命令
        cmd = [exiftool_path] + args
        
        # 执行命令
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        
        return result.stdout, result.stderr
    except subprocess.CalledProcessError as e:
        print(f"执行exiftool命令失败: {e}")
        print(f"错误输出: {e.stderr}")
        raise
    except FileNotFoundError as e:
        print(f"exiftool路径配置无效或程序不存在: {e}")
        Configer.set("exiftoolPath", "")
        raise ValueError(f"exiftool路径配置无效，请检查config.json中的exiftoolPath设置: {e}")
    except Exception as e:
        print(f"执行exiftool命令时发生错误: {e}")
        raise