# digital-signature

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/2e7111f7beb84222bdf07f8fc3c788f9)](https://app.codacy.com/app/baloise/digital-signature?utm_source=github.com&utm_medium=referral&utm_content=baloise/digital-signature&utm_campaign=Badge_Grade_Dashboard)

A macro for Atlassian Confluence

## Description
Allows you to enter a contract into a confluence macro and to sign it as logged in user.
- content and signatures can not be modified once signed
- white list users who can sign
- report when and by whom the contract was signed
- easily send email to signers of the contract
- receive notifications, when your contract was signed

## Installation
Install via [Atlassian Marketplace](https://marketplace.atlassian.com/plugins/com.baloise.confluence.digital-signature).

## Feature overview
### Insert / edit macro
![](./docs/img/contract_text.png)
![](./docs/img/edit.png)

### Sign
![](./docs/img/sign.png)

- Set signers, title notified users and layout of the contract
- One click approval. User management is done by Confluence.
- The signature remains valid only as long the title and body are the same as at the time of signature.

### Markdown
![](./docs/img/markdown.png)

### Mail notification
![](./docs/img/report_email_export.png)
![](./docs/img/send_mail.png.png)

## Builds
[![CI Build State](https://github.com/baloise/digital-signature/workflows/CI/badge.svg)](https://github.com/baloise/digital-signature/actions?query=workflow%3A%22CI%22)

[Build Artifacts](https://github.com/baloise/digital-signature/tree/gh-pages/release)
