"""Builds portable desktop bundles WITHOUT npm: takes the official Electron runtime zip from GitHub
releases and drops the app into resources/app. Usage: python3 tools/pack_desktop.py win32-x64 linux-x64"""
import os, shutil, subprocess, sys, zipfile, json

VERSION = '33.2.0'
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.environ.get('ELECTRON_CACHE_DIR', os.path.join(ROOT, '.electron-cache'))
OUT = os.path.join(ROOT, 'dist-portable')
CA = os.environ.get('CURL_CA_BUNDLE') or os.environ.get('SSL_CERT_FILE')

def fetch(platform):
    os.makedirs(CACHE, exist_ok=True)
    name = f'electron-v{VERSION}-{platform}.zip'
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        url = f'https://github.com/electron/electron/releases/download/v{VERSION}/{name}'
        cmd = ['curl', '-fL', '-o', path, url]
        if CA: cmd[1:1] = ['--cacert', CA]
        subprocess.check_call(cmd)
    return path

def unzip_keep_modes(zpath, dest):
    with zipfile.ZipFile(zpath) as z:
        for info in z.infolist():
            z.extract(info, dest)
            mode = info.external_attr >> 16
            if mode:
                os.chmod(os.path.join(dest, info.filename), mode)

def put_app(resources_dir):
    app = os.path.join(resources_dir, 'app')
    shutil.rmtree(app, ignore_errors=True)
    os.makedirs(app)
    for f in ('main.js', 'icon.png'):
        shutil.copy(os.path.join(ROOT, 'desktop', f), app)
    shutil.copytree(os.path.join(ROOT, 'desktop', 'www'), os.path.join(app, 'www'))
    pkg = json.load(open(os.path.join(ROOT, 'desktop', 'package.json'), encoding='utf8'))
    json.dump({k: pkg[k] for k in ('name', 'productName', 'version', 'description', 'main')},
              open(os.path.join(app, 'package.json'), 'w', encoding='utf8'), ensure_ascii=False, indent=2)
    shutil.rmtree(os.path.join(resources_dir, 'default_app.asar'), ignore_errors=True)
    if os.path.exists(os.path.join(resources_dir, 'default_app.asar')):
        os.remove(os.path.join(resources_dir, 'default_app.asar'))

def build(platform):
    name = f'AlifABC-{platform}'
    dest = os.path.join(OUT, name)
    shutil.rmtree(dest, ignore_errors=True)
    os.makedirs(dest)
    unzip_keep_modes(fetch(platform), dest)
    put_app(os.path.join(dest, 'resources'))
    if platform.startswith('win32'):
        os.rename(os.path.join(dest, 'electron.exe'), os.path.join(dest, 'Alif ABC.exe'))
        open(os.path.join(dest, 'README.txt'), 'w', encoding='utf8').write(
            'Double-click "Alif ABC.exe" to start the app. No installation and no internet are needed.\n'
            'Press F11 for full screen.\n')
    elif platform.startswith('linux'):
        os.rename(os.path.join(dest, 'electron'), os.path.join(dest, 'alif-abc'))
        run = os.path.join(dest, 'run.sh')
        open(run, 'w').write('#!/bin/sh\ncd "$(dirname "$0")"\nexec ./alif-abc --no-sandbox "$@"\n')
        os.chmod(run, 0o755)
    print('built', dest)
    return dest

if __name__ == '__main__':
    for p in sys.argv[1:] or ['win32-x64', 'linux-x64']:
        build(p)
