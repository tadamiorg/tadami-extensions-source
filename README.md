# Tadami-extensions
Extensions source code, you can find the corresponding apks in the [tadami-extensions](https://github.com/tadamiorg/tadami-extensions) repo.

## Releasing extensions
Run the **Release extensions** workflow from the Actions tab and list the extensions to release, as `<lang>.<name>` separated by spaces or commas (e.g. `en.animepahe fr.franime`). It builds only those, then commits their APK, icon and index entry to the [tadami-extensions](https://github.com/tadamiorg/tadami-extensions) repo. The other extensions stay as they are, including the ones left out of the index on purpose. Releasing an extension lists it again. Tick *dry run* to build and check without pushing.

Bump `extVersionCode` first: a version code that isn't above the published one is refused, and so is an APK signed with another key than the published one.

Repository secrets (what each one does and how to create it: [CI_SETUP.md](https://github.com/tadamiorg/tadami/blob/develop/docs/CI_SETUP.md) in the app repo):
- `EXTENSIONS_KEYSTORE_BASE64`: the extensions keystore (`KEY_FILE` in `.env`), base64-encoded
- `EXTENSIONS_KEY_STORE_PASSWORD`: its password (`KEY_STORE_PASSWORD`)
- `EXTENSIONS_KEY_ALIAS`: the key alias (`ALIAS`)
- `EXTENSIONS_REPO_TOKEN`: a fine-grained token with *Contents: Read and write* on tadamiorg/tadami-extensions
