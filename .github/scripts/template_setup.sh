#!/bin/sh
# Sets up a repository created from the template, run from its root by .github/workflows/template-setup.yml:
#
#     sh .github/scripts/template_setup.sh <owner>/<repo>
#
# - tadami.repoId (gradle.properties) becomes <owner>/<repo>: its new sources get IDs of their own
# - the official extensions (extensions/*/*) are replaced by the template's example, as extensions/en/example
# - README.md becomes template/README.md, for <owner>/<repo>
# - template/ and the .template marker are removed, so the setup never runs again
# The workflows are left as they are: the workflow token can't change them.
set -eu

repo=${1:?usage: template_setup.sh <owner>/<repo>}
case "$repo" in
    */*/* | /* | */ | *[!A-Za-z0-9_./-]*) echo "\"$repo\" is not <owner>/<repo>" >&2; exit 1 ;;
    */*) ;;
    *) echo "\"$repo\" is not <owner>/<repo>" >&2; exit 1 ;;
esac
owner=${repo%%/*}

for required in .template template/example template/README.md gradle.properties; do
    if [ ! -e "$required" ]; then
        echo "No $required: not the template, or already set up" >&2
        exit 1
    fi
done
if ! grep -q '^tadami\.repoId=' gradle.properties; then
    echo "No tadami.repoId in gradle.properties" >&2
    exit 1
fi

sed -i "s|^tadami\.repoId=.*|tadami.repoId=$repo|" gradle.properties

rm -rf extensions
mkdir -p extensions/en
cp -R template/example extensions/en/example

sed -e "s|{{REPO}}|$repo|g" -e "s|{{OWNER}}|$owner|g" template/README.md > README.md

rm -rf template .template
echo "Set up $repo: tadami.repoId=$repo, extensions/en/example, README.md"
