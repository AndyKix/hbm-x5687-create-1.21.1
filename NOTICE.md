# Attribution

This is an independent, incomplete port of Hbm's Nuclear Tech Mod.
Original authors: HbMinecraft / The Bobcat and the HBM contributors credited in
the upstream repository and original mod metadata.

Source: https://github.com/HbmMods/Hbm-s-Nuclear-Tech-GIT
Tag: `1.0.27_X5687`
Commit: `23ef33447280d83d44c1f7bcd3853fe59c0ec1fc`

The material textures, iron-furnace texture and GUI are copied without modification
from that source. Iron-furnace OBJ vertex/UV/normal data are preserved; group
selection and material bindings are adapted to the NeoForge OBJ loader.
See `iron-furnace-provenance.json` and `tools/import_iron_furnace.py`.
Iron/steel crate textures, GUIs and sounds are copied byte-for-byte from X5687;
sound filenames are lowercased for modern resource identifiers.
See `crate-provenance.json` and `tools/import_crates.py`.
See `provenance.json` for checksums and `LICENSE` / `LICENSE.LESSER` for the
upstream license. This derivative is distributed under LGPL-3.0-or-later.
The original HBM mod and its developers do not endorse this port.

Create is a separate dependency. Its code and binaries are not redistributed in
this project's mod JAR. Reference version: Create 6.0.10 for Minecraft 1.21.1.
Source: https://github.com/Creators-of-Create/Create/tree/mc1.21.1-6.0.10

Natural resource textures/translations and source-derived mineral/worldgen rules
are imported from the same pinned X5687 commit. See raw-material-provenance.json,
tools/import_raw_materials.py and STAGE5_MAPPING.md. DepthDeposit, SchistStratum,
OreLayer3D, OreCave, BlockLithium and IDepthRockTool algorithms are adapted to
NeoForge's native generation/lifecycle APIs. No modern HBM port is used.

The HE and water/lava foundation adapts X5687 battery, socket, cable, steel barrel,
fluid identifier and typed network behavior to native NeoForge components,
capabilities, menus and loaded-chunk lifecycle. Source textures/GUI are copied
byte-for-byte; original OBJ groups are selected, origin/orientation translated
and native materials/colors bound. See infrastructure-provenance.json,
tools/import_infrastructure.py and STAGE6_MAPPING.md for exact scope and changes.

The base electric furnace adapts TileEntityMachineElectricFurnace and its native
GUI/automation to 1.21.1; original five block textures and GUI are copied unchanged.
See electric-furnace-provenance.json and STAGE7_MAPPING.md for scope and differences.
