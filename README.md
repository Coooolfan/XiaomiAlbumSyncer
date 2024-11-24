# Xiaomi Album Syncer

此项目旨在提供一个Python程序，用于将小米云服务中的相册照片单向同步到本地。

# 使用方法

TODO……

# Development

## 安装依赖

```bash
pip install -r requirements.txt
```

## 获取Cookie

### 使用油猴脚本快速复制

1. 安装[油猴脚本](https://www.tampermonkey.net/)。
2. 安装`小米云Cookie获取脚本`：本仓库根目录`tampermonkey.js`文件
3. 登录[小米云](https://i.mi.com/)。
4. 点击左下角的`Show Cookies`按钮。
5. `Cookie`字段值会自动复制到剪贴板, 粘贴到`config.json`中的`cookie`字段。

### 手动获取

1. 登录[小米云](https://i.mi.com/)。
2. 打开浏览器的开发者工具（F12）。
3. 在开发者工具中切换到`Network`选项卡。
4. 刷新页面。
5. 在开发者工具中找到`i.mi.com`的请求，复制`Request Headers`中的`Cookie`字段值。
6. 粘贴到`config.json`中的`cookie`字段。

## 编译
    
```bash
    pyinstaller --onefile --add-data "requirements.txt;." main.py
```
