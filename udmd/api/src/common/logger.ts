/**
 * Logger object used by the microservice to log information to the console by default following the 12 factor app principles.
 * The console output is then aggregated at the AWS CloudWatch level
 */
import { createLogger, format, transports, Logger } from 'winston';
import { Format } from 'logform';

// formatting output function
const formatLogMsg = ({ timestamp, level, message }) => {
  return `${timestamp ? timestamp + ' ' : ''}${level}: ${JSON.stringify(message)}`;
};

let loggerFormat: Format = format.combine(format.splat(), format.printf(formatLogMsg));

export const logger: Logger = createLogger({
  level: process.env.LOG_LEVEL || 'debug',
  format: loggerFormat,
  silent: false,
  transports: [
    new transports.Console({
      handleExceptions: true,
    }),
  ],
  exitOnError: false, // do not exit on handled exceptions
});
