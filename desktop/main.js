'use strict';
const { app, BrowserWindow, Menu, session, shell } = require('electron');
const path = require('path');

const gotLock = app.requestSingleInstanceLock();
let win = null;

function createWindow() {
  win = new BrowserWindow({
    width: 1100,
    height: 800,
    minWidth: 360,
    minHeight: 560,
    show: false,
    title: 'الگراي',
    backgroundColor: '#EAF3FF',
    icon: path.join(__dirname, 'icon.png'),
    autoHideMenuBar: true,
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true, spellcheck: false }
  });
  Menu.setApplicationMenu(null);
  win.loadFile(path.join(__dirname, 'www', 'index.html'));
  win.once('ready-to-show', () => win.show());

  // The app is fully local: never navigate away, open web links in the system browser.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/i.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('file://')) e.preventDefault(); });

  // F11 toggles full screen, Esc leaves it.
  win.webContents.on('before-input-event', (e, input) => {
    if (input.type !== 'keyDown') return;
    if (input.key === 'F11') { win.setFullScreen(!win.isFullScreen()); e.preventDefault(); }
    else if (input.key === 'Escape' && win.isFullScreen()) { win.setFullScreen(false); }
  });
  win.on('closed', () => { win = null; });
}

if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
  app.whenReady().then(() => {
    session.defaultSession.setPermissionRequestHandler((wc, permission, cb) => cb(false)); // the app needs none
    createWindow();
    app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
  });
  app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
}
