#!/usr/bin/env sh
# Copies the web app (www/) into the Android and desktop projects. Run after every change to www/.
set -e
cd "$(dirname "$0")/.."
rm -rf android/app/src/main/assets/www desktop/www
mkdir -p android/app/src/main/assets desktop
cp -R www android/app/src/main/assets/www
cp -R www desktop/www
# the service worker is only for the web (PWA) build
rm -f android/app/src/main/assets/www/sw.js desktop/www/sw.js
echo "synced www -> android/app/src/main/assets/www and desktop/www"
