module.exports = {
  roots: ['<rootDir>/src'],
  transform: {
    '^.+\\.tsx?$': 'ts-jest',
  },
  reporters: [
    "default",
  ],
  collectCoverage: true,
  coveragePathIgnorePatterns: ["/node_modules/", "/src/__tests__/"],
  coverageReporters: [
    "text",
    "cobertura",
    "lcov"
  ],
  // testEnvironment: "node",
  testRegex: '(/__tests__/spec.ts|(\\.|/)(test|spec))\\.tsx?$',
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json', 'node'],
  watchPathIgnorePatterns: ['globalConfig'],
  setupFilesAfterEnv: [
    './setupTests.js',
  ],
}