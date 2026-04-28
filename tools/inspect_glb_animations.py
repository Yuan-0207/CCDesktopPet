import argparse
from pathlib import Path

import bpy


def inspect_glb(glb_path: Path):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(glb_path))
    actions = list(bpy.data.actions)
    if not actions:
        return None
    action = actions[0]
    frame_start, frame_end = action.frame_range
    fps = bpy.context.scene.render.fps or 24
    duration = (frame_end - frame_start + 1) / fps
    return action.name, frame_start, frame_end, duration


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    argv = __import__("sys").argv
    if "--" in argv:
        argv = argv[argv.index("--") + 1 :]
    else:
        argv = []
    args = parser.parse_args(argv)

    root = Path(args.root)
    glbs = sorted(root.rglob("*.glb"))
    print(f"GLB_COUNT|{len(glbs)}")
    for index, glb in enumerate(glbs):
        result = inspect_glb(glb)
        if result is None:
            print(f"{index}|{glb.name}|NO_ACTION|||")
            continue
        action_name, frame_start, frame_end, duration = result
        print(
            f"{index}|{glb.name}|{action_name}|"
            f"{frame_start:.1f}-{frame_end:.1f}|{duration:.2f}s"
        )


if __name__ == "__main__":
    main()

