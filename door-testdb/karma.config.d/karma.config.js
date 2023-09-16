//Workaround resource loader bug as per notes on build.gradle
config.middleware = config.middleware || [];
config.middleware.push('resource-loader');

function ResourceLoaderMiddleware() {
    const fs = require('fs');

    return function (request, response, next) {
        try {
            console.log(PROJECT_PATH);
            const content = fs.readFileSync(PROJECT_PATH + '/door-runtime/build/processedResources/js/test' + decodeURI(request.originalUrl));
            response.writeHead(200);
            response.end(content);
        } catch (ignored) {
            try {
                const content = fs.readFileSync(PROJECT_PATH + '/door-runtime/build/processedResources/js/main' + decodeURI(request.originalUrl));
                response.writeHead(200);
                response.end(content);
            } catch (ignored) {
                next();
            }
        }
    }
}


config.set({
  logLevel: config.LOG_DEBUG,
  client: {
    mocha: {
      timeout: 30000
    }
  }
})


config.plugins.push({
    'middleware:resource-loader': ['factory', ResourceLoaderMiddleware]
});