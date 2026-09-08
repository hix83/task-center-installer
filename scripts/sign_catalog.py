#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Sign catalog/catalog.json with an offline RSA key; never commit the private key."""
import argparse,base64,json,pathlib,subprocess,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--key',type=pathlib.Path,required=True);a=p.parse_args()
public=subprocess.run(['openssl','pkey','-in',str(a.key),'-pubout','-outform','DER'],check=True,capture_output=True).stdout
if base64.b64encode(public).decode() not in (ROOT/'app/src/local/taskcenter/launcher/CatalogKey.java').read_text():
 raise SystemExit('Private key does not match the public key pinned by this app')
data=(ROOT/'catalog/catalog.json').read_bytes();obj=json.loads(data)
assert obj['schema']==1 and obj['revision']>=1
with tempfile.TemporaryDirectory() as tmp:
 sig=pathlib.Path(tmp)/'signature'
 subprocess.run(['openssl','dgst','-sha256','-sign',str(a.key),'-out',str(sig),str(ROOT/'catalog/catalog.json')],check=True)
 envelope=json.dumps({'payload':base64.b64encode(data).decode(),'signature':base64.b64encode(sig.read_bytes()).decode()},separators=(',',':'))+'\n'
 for path in [ROOT/'catalog/catalog.signed.json',ROOT/'app/assets/catalog.signed.json']:path.write_text(envelope)
print('Signed catalog revision',obj['revision'])
