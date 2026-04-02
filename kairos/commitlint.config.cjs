const TYPES = [
  "build",
  "chore",
  "ci",
  "docs",
  "feat",
  "fix",
  "perf",
  "refactor",
  "revert",
  "style",
  "test",
];

const SCOPES = ["client", "server", "shared", "ci", "deps"];

module.exports = {
  parserPreset: {
    parserOpts: {
      headerPattern:
        /^(build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test)(?:\((client|server|shared|ci|deps)\))?: (.+)$/,
      headerCorrespondence: ["type", "scope", "subject"],
    },
  },
  rules: {
    "header-max-length": [2, "always", 100],
    "type-case": [2, "always", "lower-case"],
    "type-empty": [2, "never"],
    "type-enum": [2, "always", TYPES],
    "scope-case": [2, "always", "lower-case"],
    "scope-enum": [2, "always", SCOPES],
    "subject-empty": [2, "never"],
    "subject-full-stop": [2, "never", "."],
  },
  helpUrl:
    "Commit format: type(scope): subject or type: subject. Allowed scopes: client, server, shared, ci, deps.",
};
