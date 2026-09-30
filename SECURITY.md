# Security Policy

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Report them privately through GitHub instead:
**Security** tab → **Report a vulnerability**
([direct link](https://github.com/afunemma/salon-booking-api/security/advisories/new)).

Please include:

- what the problem is and where it is in the code
- steps to reproduce it
- what an attacker could do with it

I aim to reply within 7 days.

## Supported versions

This project is in active development. Only the latest code on `main` receives fixes.

## How this repository is protected

- **Secret scanning with push protection:** pushes containing real credentials are blocked.
- **Dependabot:** alerts and automatic pull requests for vulnerable or outdated dependencies.
- **CodeQL:** scans the code for security issues on every push and pull request.
- **Branch ruleset:** `main` cannot be deleted or have its history overwritten.
