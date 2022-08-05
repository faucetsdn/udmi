module.exports = {
    mongodbMemoryServerOptions: {
        binary: {
            skipMD5: true,
        },
        instance: {
            dbName: 'tmp',
        },
        autoStart: false,
    },
};