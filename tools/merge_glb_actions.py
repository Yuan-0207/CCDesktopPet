import argparse
import sys
from pathlib import Path

import bpy


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-dir", required=True, help="Directory containing per-action GLB files")
    parser.add_argument("--output", required=True, help="Output merged GLB path")
    argv = sys.argv
    if "--" in argv:
        argv = argv[argv.index("--") + 1 :]
    else:
        argv = []
    return parser.parse_args(argv)


def clear_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def import_glb(path: Path):
    before_objs = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(path))
    imported = [obj for obj in bpy.data.objects if obj not in before_objs]
    return imported


def armature_action_name(action_name: str) -> str:
    parts = action_name.split("|")
    if len(parts) >= 2:
        return parts[1]
    return action_name


def delete_objects(objs):
    for obj in objs:
        obj.select_set(True)
    bpy.ops.object.delete()


def main():
    args = parse_args()
    input_dir = Path(args.input_dir)
    output = Path(args.output)
    glb_files = sorted(input_dir.rglob("*.glb"))
    if not glb_files:
        raise RuntimeError(f"No glb files found under {input_dir}")

    clear_scene()

    # Import first file as base model.
    base_imported = import_glb(glb_files[0])
    armatures = [obj for obj in base_imported if obj.type == "ARMATURE"]
    if not armatures:
        raise RuntimeError("No armature found in base GLB")
    base_armature = armatures[0]

    collected = []
    if base_armature.animation_data and base_armature.animation_data.action:
        action = base_armature.animation_data.action
        action.name = armature_action_name(action.name)
        action.use_fake_user = True
        collected.append(action.name)

    # Import each additional file, collect its action, then delete imported objects.
    for glb in glb_files[1:]:
        imported_objs = import_glb(glb)
        imported_armatures = [obj for obj in imported_objs if obj.type == "ARMATURE"]
        if not imported_armatures:
            delete_objects(imported_objs)
            continue
        arm = imported_armatures[0]
        if arm.animation_data and arm.animation_data.action:
            action = arm.animation_data.action
            action.name = armature_action_name(action.name)
            action.use_fake_user = True
            collected.append(action.name)
        delete_objects(imported_objs)

    # Ensure base armature has one active action.
    if collected:
        base_armature.animation_data_create()
        base_armature.animation_data.action = bpy.data.actions[collected[0]]

    output.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=str(output),
        export_format="GLB",
        export_animations=True,
        export_animation_mode="ACTIONS",
        export_apply=True,
    )
    print(f"MERGED_ACTION_COUNT|{len(collected)}")
    for idx, name in enumerate(collected):
        print(f"{idx}|{name}")
    print(f"OUTPUT|{output}")


if __name__ == "__main__":
    main()

