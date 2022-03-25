FROM node:16.13.2

ENV PORT 4300

RUN apt-get update && apt-get install curl -y

WORKDIR /usr/src/app

COPY udmd/api/package*.json ./
COPY udmd/api/tsconfig.json .
COPY udmd/api/.npmrc .
COPY udmd/api/src src

RUN npm install

EXPOSE $PORT

CMD ["npm", "start"]
