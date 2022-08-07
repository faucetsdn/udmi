# run the install which will grab all the dependencies
npm install

# leave existing .env's alone since it may have some custom values, else we copy the template into the .env
[ ! -f .env ] && cp .env.example .env