FROM node:12.22.9-slim

ENV PORT 4300

RUN apt-get update && apt-get install curl -y

WORKDIR /usr/src/app

COPY package*.json ./
COPY tsconfig.json .
COPY .npmrc .
COPY src src

RUN npm install

EXPOSE $PORT

CMD ["npm", "start"]
