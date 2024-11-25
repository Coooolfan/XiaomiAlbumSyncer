FROM python:3.12-slim

WORKDIR /app

LABEL name="XiaomiAlbumSyncer" authors="Coooolfan" repository="https://github.com/Coooolfan/XiaomiAlbumSyncer"

COPY src /app/src
COPY main.py /app/main.py
COPY requirements.txt /app/requirements.txt

RUN pip install --no-cache-dir -r /app/requirements.txt

VOLUME /app

CMD ["python", "main.py"]