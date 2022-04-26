FROM nginx:1.21.5-alpine

ENV BUILD_DIR dist/udmif-web
ENV WEB_DIR /usr/share/nginx/html 
ENV HOME_DIR /usr/share/nginx

WORKDIR $WEB_DIR

# Copy the artifacts from the webs build into nginx to be served.
COPY $BUILD_DIR $WEB_DIR

# Copy the custom nginx template file into the nginx folder
# to allow envsubst to pick it up and generate a nginx.conf file
# with the replaced docker runtime env variables.
COPY prod/nginx.conf /etc/nginx

# Copy the docker entrypoint file to the root.
COPY prod/docker-entrypoint.sh /

# Just in case the file mode was not properly set in Git.
RUN chmod +x /docker-entrypoint.sh

# Use our own entrypoint.
ENTRYPOINT ["/docker-entrypoint.sh"]

# Launch nginx.
CMD ["nginx", "-g", "daemon off;"]
