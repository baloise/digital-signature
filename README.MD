# digital-signature
[![CI Build State](https://github.com/baloise/digital-signature/workflows/CI/badge.svg)](https://github.com/baloise/digital-signature/actions?query=workflow%3A%22CI%22)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/2e7111f7beb84222bdf07f8fc3c788f9)](https://app.codacy.com/app/baloise/digital-signature?utm_source=github.com&utm_medium=referral&utm_content=baloise/digital-signature&utm_campaign=Badge_Grade_Dashboard)

A digital signature macro for [Atlassian Confluence](https://www.atlassian.com/software/confluence)

## Description
Allows confluence users to write contracts in a confluence macro which can be signed directly by logged-in users.
- content and signatures can not be modified once signed
- white list users who can sign
- report when and by whom the contract was signed
- easily send email to signers of the contract
- receive notifications, when your contract was signed

## Privacy Policy
- We do not transfer or store any data outside your Atlassian product.
- We have no access to any data you stored within your Atlassian product.
- Your data is yours - no strings attached.

## Installation & Usage
Install via [Atlassian Marketplace](https://marketplace.atlassian.com/plugins/com.baloise.confluence.digital-signature).

A detailed description of the available configuration fields and usage is described in
the [Wiki...](https://github.com/baloise/digital-signature/wiki/Signature-Macro-Usage)

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

## Contribute
Keep it simple: every contribution is welcome. Either if you report an issue, help on solving one, or contribute to the
solution by adding a Pull Request.

For a more detailed answer on best practices and guidelines please have a look at our [open source community](https://github.com/baloise/open-source) at Baloise.

## Credits
This macro was written by [Matthias Cullmann](@culmat).
It is maintained in the Open Source Community at Baloise.

## License
This project is licensed under the [Apache-2.0 License](https://github.com/baloise/digital-signature/blob/master/LICENSE).

## Releases
[Build Artifacts](https://github.com/baloise/digital-signature/tree/gh-pages/release)
