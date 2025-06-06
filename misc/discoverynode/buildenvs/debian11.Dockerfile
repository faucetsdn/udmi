FROM debian:11

WORKDIR /tmp

RUN apt update && apt -y install build-essential zlib1g-dev libncurses5-dev libgdbm-dev libnss3-dev libssl-dev libreadline-dev libffi-dev libsqlite3-dev wget openssl
RUN wget https://www.openssl.org/source/openssl-1.1.1g.tar.gz 
RUN wget https://www.python.org/ftp/python/3.12.8/Python-3.12.8.tgz
RUN tar -xzvf Python-3.12.8.tgz
WORKDIR /tmp/Python-3.12.8
RUN ./configure --enable-optimizations --with-ensurepip=install CFLAGS="-I/usr/include" LDFLAGS="-Wl,-rpath /usr/local/lib" --enable-shared --prefix=/usr/local
RUN make -j3
RUN make install

RUN python3 -m pip install pyinstaller

WORKDIR /rootex