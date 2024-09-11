FROM python:3.11-bookworm

WORKDIR /usr/src/app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY bacnet_server.py .

CMD [ "python", "bacnet_server.py" ]