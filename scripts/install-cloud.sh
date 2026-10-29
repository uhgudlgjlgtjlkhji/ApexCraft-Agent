#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
export JAVA_HOME=/workspace/toolchains/jdk-25.0.5+8
export GRADLE_USER_HOME=/workspace/.apexcraft-gradle
if [ ! -x "$JAVA_HOME/bin/javac" ]; then
python - <<'PY'
import hashlib
from pathlib import Path
import tarfile
import urllib.request
folder=Path('/workspace/toolchains')
folder.mkdir(parents=True,exist_ok=True)
name='OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz'
url='https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/'+name
archive=folder/name
urllib.request.urlretrieve(url,archive)
expected='68982529869257d0ee6b824ec5aa270ff8b118bb438670da0b97ebdab178d7fb'
with archive.open('rb') as handle:
    actual=hashlib.file_digest(handle,'sha256').hexdigest()
if actual != expected:
    raise RuntimeError('Official Java archive checksum mismatch')
with tarfile.open(archive) as handle:
    handle.extractall(folder,filter='data')
PY
fi
scripts/test-java-core.sh
python -m unittest discover -s tests -v
exec scripts/cloud-build.sh build
