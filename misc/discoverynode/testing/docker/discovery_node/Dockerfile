FROM python:3.12-bookworm

WORKDIR /usr/src/app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
RUN apt update && apt install -y tcpdump
COPY . .

# ENTRYPOINT ["sh", "/usr/src/app/entrypoint.sh"]
CMD ["python3", "main.py", "config.toml"]