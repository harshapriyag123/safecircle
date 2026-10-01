"""Offline emulator smoke: free session workflow and startup scheduling, no purchases/providers."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = 'com.harshapriya.safecircle'
OUT = Path('app/build/device-evidence'); OUT.mkdir(parents=True, exist_ok=True)

def adb(*args):
    return subprocess.check_output(['adb', *args], text=True).strip()

def ui():
    adb('shell', 'uiautomator', 'dump', '/sdcard/safecircle-ui.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/safecircle-ui.xml'))

def tap(resource):
    for attempt in range(6):
        for node in ui().iter('node'):
            if node.get('resource-id') == PACKAGE + ':id/' + resource and node.get('enabled') == 'true':
                bounds=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
                if len(bounds)==4 and bounds[2]>bounds[0] and bounds[3]>bounds[1]:
                    adb('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
                    time.sleep(2)
                    return
        adb('shell','input','swipe','540','1700','540','650','400'); time.sleep(1)
    raise RuntimeError('Visible enabled control missing: '+resource)

def screenshot(name):
    data=subprocess.check_output(['adb','exec-out','screencap','-p'])
    (OUT/name).write_bytes(data)

adb('install','-r','/tmp/safecircle-apk/app-debug.apk')
for permission in ('ACCESS_COARSE_LOCATION','ACCESS_FINE_LOCATION','POST_NOTIFICATIONS'):
    adb('shell','pm','grant',PACKAGE,'android.permission.'+permission)
adb('shell','am','start','-n',PACKAGE+'/.onboarding.OnboardingActivity')
time.sleep(3)
tap('onboardingContinueButton')
if not adb('shell','pidof',PACKAGE): raise RuntimeError('App did not remain running')
if PACKAGE not in adb('shell','dumpsys','jobscheduler'):
    raise RuntimeError('Background work was not scheduled at startup')
screenshot('native-home.png')
tap('startSessionButton'); tap('startConfiguredSessionButton')
screenshot('native-active-session.png')
tap('checkInButton'); tap('extend5Button'); tap('safeButton')
adb('shell','input','swipe','540','650','540','1800','500'); time.sleep(2)
if not any(node.get('text')=='RESOLVED' for node in ui().iter('node')):
    raise RuntimeError('Mark safe did not reach terminal state')
screenshot('native-resolved.png')
tap('navigation_vault'); screenshot('native-privacy.png')
tap('navigation_profile'); screenshot('native-profile.png')
(OUT/'result.txt').write_text('APK installed; app stayed running; startup background job registered; offline session started, checked in, extended and resolved. No purchase or external alert was attempted.\n')
print('Native offline workflow and startup scheduling passed; screenshots captured.')
