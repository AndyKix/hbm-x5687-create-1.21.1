#!/usr/bin/env python3
"""Reproduce the complete current X5687 asset/data slice in dependency order."""
import argparse
from pathlib import Path
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('upstream', type=Path)
upstream = parser.parse_args().upstream.resolve()
for script in ('import_materials.py', 'import_iron_furnace.py', 'import_crates.py', 'import_raw_materials.py', 'import_infrastructure.py'):
    subprocess.run([sys.executable, str(Path(__file__).with_name(script)), str(upstream)], check=True)
