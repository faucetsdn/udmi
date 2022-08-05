FROM node:16.13.2-slim

ENV USERNAME api

#Create the user for the application and its home directory
RUN useradd --user-group --create-home --shell /bin/false $USERNAME
ENV USER_HOME /home/$USERNAME

RUN chown -R $USERNAME:$USERNAME $USER_HOME

USER $USERNAME
WORKDIR $USER_HOME
VOLUME $USER_HOME