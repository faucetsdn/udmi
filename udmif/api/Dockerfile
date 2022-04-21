FROM node:16.13.2

ENV PORT 4300

RUN apt-get update && apt-get install curl -y

WORKDIR /usr/src/app

COPY udmif/api/package*.json ./
COPY udmif/api/tsconfig.json .
COPY udmif/api/.npmrc .
COPY udmif/api/src src

RUN npm install

EXPOSE $PORT

CMD ["npm", "start"]
