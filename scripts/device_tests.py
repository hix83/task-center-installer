#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Build and run explicit device tests against an installed release signed with the same key."""
import argparse,os,pathlib,subprocess,zipfile
R=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--sdk',required=True);p.add_argument('--keystore',required=True);p.add_argument('--network',action='store_true');p.add_argument('--catalog',action='store_true');a=p.parse_args()
sdk=pathlib.Path(a.sdk);bt=sdk/'build-tools/36.0.0';android=sdk/'platforms/android-35/android.jar';b=R/'build/device-tests'
for folder in [b,b/'classes',b/'dex']:folder.mkdir(parents=True,exist_ok=True)
def run(*cmd):subprocess.run([str(x) for x in cmd],check=True)
run(bt/'aapt','package','-f','-M',R/'tests/AndroidManifest.xml','-I',android,'-F',b/'unsigned.apk')
# Compile target sources for type checking, but include only the instrumentation class in the test APK.
run('javac','-source','8','-target','8','-encoding','UTF-8','-classpath',android,'-d',b/'classes',*sorted((R/'app/src').rglob('*.java')),R/'tests/CatalogRuntimeTest.java')
run(bt/'d8','--min-api','28','--lib',android,'--output',b/'dex',b/'classes/local/taskcenter/launcher/CatalogRuntimeTest.class')
with zipfile.ZipFile(b/'unsigned.apk','a')as z:z.write(b/'dex/classes.dex','classes.dex')
run(bt/'zipalign','-f','4',b/'unsigned.apk',b/'aligned.apk')
run(bt/'apksigner','sign','--ks',a.keystore,'--ks-key-alias','launcher','--ks-pass','env:TASK_CENTER_STORE_PASS','--out',b/'tests.apk',b/'aligned.apk')
adb=sdk/'platform-tools/adb'
run(adb,'-s',a.serial,'install','--no-incremental','--user','0','-r',b/'tests.apk')
try:
 cmd=[adb,'-s',a.serial,'shell','am','instrument','--user','0','-w']
 if a.network:cmd+=['-e','network','true']
 if a.catalog:cmd+=['-e','catalog','true']
 cmd+=['local.taskcenter.tests/local.taskcenter.launcher.CatalogRuntimeTest']
 result=subprocess.run([str(x)for x in cmd],check=True,capture_output=True,text=True)
 print(result.stdout)
 if 'PASS:' not in result.stdout or 'FAIL:' in result.stdout:raise SystemExit('Device tests failed')
finally:run(adb,'-s',a.serial,'uninstall','local.taskcenter.tests')
