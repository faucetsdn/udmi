
# clear all the old *.js artifacts
rm -rf dist

# remove all the installed dependencies
rm -rf node_modules

# install all the dependencies from package.json
npm install

# generate new artifacts i.e. transpile *.ts into *.js
npm run build