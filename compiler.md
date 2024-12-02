# 编译

## for Ubuntu

```bash
sudo apt-get install python3 python3-pip git

git clone https://github.com/Coooolfan/XiaomiAlbumSyncer.git
# git clone https://kkgithub.com/Coooolfan/XiaomiAlbumSyncer.git
# git clone https://githubfast.com/Coooolfan/XiaomiAlbumSyncer.git
cd XiaomiAlbumSyncer
pip install -r requirements.txt
# test
# python3 main.py
pip install pyinstaller
pyinstaller --onefile --add-data "requirements.txt:." main.py
# /home/ubuntu/.local/bin/pyinstaller --onefile --add-data "requirements.txt:." main.py
```