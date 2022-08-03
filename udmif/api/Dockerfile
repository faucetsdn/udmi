FROM node:16.13.2

ENV PORT 4300

RUN apt-get update && apt-get install curl -y

WORKDIR /usr/src/app

COPY package*.json ./
COPY tsconfig.json .
COPY src src

RUN npm install

EXPOSE $PORT

CMD ["npm", "run", "production"]
