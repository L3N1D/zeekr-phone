"""Resolve the compile-only API of the scanner already shipped in the stock APK."""
import hashlib
import os
from pathlib import Path
import tempfile
import urllib.request
import zipfile

cache = Path.home() / ".cache/zeekr-phone/scanner"
cache.mkdir(parents=True, exist_ok=True)

def verified(path, digest):
    return path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == digest

def save(path, data):
    with tempfile.NamedTemporaryFile(dir=cache, delete=False) as out:
        out.write(data)
    os.replace(out.name, path)

def artifact(relative, digest):
    path = cache / relative.split("/")[-1]
    if not verified(path, digest):
        with urllib.request.urlopen("https://repo.maven.apache.org/maven2/" + relative, timeout=30) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != digest:
            raise ValueError("Dependency checksum mismatch: " + path.name)
        save(path, data)
    return path

aar = artifact("com/journeyapps/zxing-android-embedded/4.3.0/zxing-android-embedded-4.3.0.aar",
               "4ab03353127c34e55cbb80fbc9a34031decbcfb1ec5e8f991944d2d494621a33")
core = artifact("com/google/zxing/core/3.5.3/core-3.5.3.jar",
                "8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82")
api = cache / "scanner-api.jar"
if not verified(api, "17fd85d4311dc98cc1c81e2832f83f5d91400077ca8d206d6d6584391a6c3958"):
    with zipfile.ZipFile(aar) as archive:
        save(api, archive.read("classes.jar"))
print(os.pathsep.join(map(str, (api, core))))
