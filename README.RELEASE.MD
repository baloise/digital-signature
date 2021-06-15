# Release
- check fixed issues are assigned to milestone: https://github.com/baloise/digital-signature/milestones
- merge onto `release` branch
- execute `mvn -B release:clean release:prepare` on `release` branch
- wait for the [![ReleaseAction](https://github.com/baloise/digital-signature/workflows/Release/badge.svg)](https://github.com/baloise/digital-signature/actions?query=workflow%3A%22Release%22) to succeed
- create a version in the [atlssian marketplace](https://marketplace.atlassian.com/manage/plugins/com.baloise.confluence.digital-signature/versions) and upload obr from [GitHub Pages](https://github.com/baloise/digital-signature/tree/gh-pages/release).
