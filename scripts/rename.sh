#!/usr/bin/env bash
# Personalise the project before the first commit.
# Usage: ./scripts/rename.sh <github-username> "<Your Full Name>"
set -euo pipefail

if [ $# -ne 2 ]; then
  echo "Usage: $0 <github-username> \"<Your Full Name>\"" >&2
  exit 1
fi

user="$1"
name="$2"
group_user="$(echo "$user" | tr '[:upper:]' '[:lower:]')"        # Maven groupId: io.github.<user>
pkg_user="$(echo "$group_user" | tr -c 'a-z0-9\n' '_')"           # Java package: hyphens -> underscores
case "$pkg_user" in [0-9]*) pkg_user="_$pkg_user" ;; esac

cd "$(dirname "$0")/.."

files=$(grep -rlI --exclude-dir=.git --exclude-dir=target --exclude=rename.sh \
  -e yourusername -e YOUR_NAME .)
for f in $files; do
  sed -i.bak \
    -e "s/io\.github\.yourusername\.mcpdiscovery/io.github.${pkg_user}.mcpdiscovery/g" \
    -e "s/io\.github\.yourusername/io.github.${group_user}/g" \
    -e "s/yourusername/${user}/g" \
    -e "s/YOUR_NAME/${name}/g" "$f"
  rm -f "$f.bak"
done

find . -type d -path '*/io/github/yourusername' -not -path './.git/*' | while read -r dir; do
  mv "$dir" "$(dirname "$dir")/${pkg_user}"
done

echo "groupId: io.github.${group_user}"
echo "package: io.github.${pkg_user}.mcpdiscovery"
echo "Done. Review with: git diff"
