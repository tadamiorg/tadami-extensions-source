# {{REPO}}

Extensions for [Tadami](https://github.com/tadamiorg/tadami), created from the
[Tadami extensions template](https://github.com/tadamiorg/tadami-extensions-source). Its
[README](https://github.com/tadamiorg/tadami-extensions-source#readme) explains how to create, build, test and
release an extension.

## Next steps

1. Make `extensions/en/example` your first extension: rename its folder, `pkgNameSuffix`, `extName`, `extClass`
   and the class, then replace the URLs and selectors with your site's. Or create yours next to it and delete it.
2. Create the repository the extensions are published to: `{{OWNER}}/tadami-extensions` (public), with a first
   commit (a README is enough).
3. Add the secrets of the **Release extensions** workflow (Settings > Secrets and variables > Actions): your
   signing key and a token that can push to `{{OWNER}}/tadami-extensions`.
4. Run **Release extensions** from the Actions tab with your extensions, e.g. `en.example`.
5. In Tadami, add `https://github.com/{{OWNER}}/tadami-extensions` in the extension repositories, and trust its key.

`tadami.repoId` in `gradle.properties` is `{{REPO}}`: your sources' IDs are generated from it, so never
change it once an extension is released. `.github/workflows/template-setup.yml` set this repository up and
won't run again: you can delete it.
