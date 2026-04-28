import argparse
import math
import os
import sys

import bpy
from mathutils import Vector


def clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for block in bpy.data.meshes:
        bpy.data.meshes.remove(block)
    for block in bpy.data.materials:
        bpy.data.materials.remove(block)
    for block in bpy.data.images:
        bpy.data.images.remove(block)


def import_glb(path: str):
    bpy.ops.import_scene.gltf(filepath=path)
    objects = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    if not objects:
        raise RuntimeError(f"No mesh found in {path}")
    return objects


def compute_bounds_world(objects):
    mins = Vector((float("inf"), float("inf"), float("inf")))
    maxs = Vector((float("-inf"), float("-inf"), float("-inf")))
    for obj in objects:
        for corner in obj.bound_box:
            world = obj.matrix_world @ Vector(corner)
            mins.x = min(mins.x, world.x)
            mins.y = min(mins.y, world.y)
            mins.z = min(mins.z, world.z)
            maxs.x = max(maxs.x, world.x)
            maxs.y = max(maxs.y, world.y)
            maxs.z = max(maxs.z, world.z)
    return mins, maxs


def center_and_scale(objects, target_size: float):
    mins, maxs = compute_bounds_world(objects)
    center = (mins + maxs) * 0.5
    size = max(maxs.x - mins.x, maxs.y - mins.y, maxs.z - mins.z)
    if size <= 0.0001:
        size = 1.0
    scale = target_size / size
    for obj in objects:
        obj.location = (obj.location - center) * scale
        obj.scale *= scale


def apply_model_transform(objects, yaw_deg: float, offset_x: float, offset_y: float, offset_z: float):
    yaw = math.radians(yaw_deg)
    for obj in objects:
        obj.rotation_euler[2] += yaw
        obj.location.x += offset_x
        obj.location.y += offset_y
        obj.location.z += offset_z


def setup_camera_and_light(cam_distance: float, cam_height: float, cam_pitch_deg: float, lens: float):
    cam_data = bpy.data.cameras.new("Camera")
    cam_obj = bpy.data.objects.new("Camera", cam_data)
    bpy.context.scene.collection.objects.link(cam_obj)
    bpy.context.scene.camera = cam_obj
    cam_obj.location = (0.0, -cam_distance, cam_height)
    cam_obj.rotation_euler = (math.radians(cam_pitch_deg), 0.0, 0.0)
    cam_data.lens = lens

    key_data = bpy.data.lights.new(name="KeyLight", type="AREA")
    key_data.energy = 1200
    key_obj = bpy.data.objects.new(name="KeyLight", object_data=key_data)
    bpy.context.scene.collection.objects.link(key_obj)
    key_obj.location = (2.0, -1.5, 2.6)
    key_obj.rotation_euler = (math.radians(55), 0.0, math.radians(25))

    fill_data = bpy.data.lights.new(name="FillLight", type="AREA")
    fill_data.energy = 450
    fill_obj = bpy.data.objects.new(name="FillLight", object_data=fill_data)
    bpy.context.scene.collection.objects.link(fill_obj)
    fill_obj.location = (-2.0, -1.0, 1.7)
    fill_obj.rotation_euler = (math.radians(50), 0.0, math.radians(-30))

    rim_data = bpy.data.lights.new(name="RimLight", type="POINT")
    rim_data.energy = 250
    rim_obj = bpy.data.objects.new(name="RimLight", object_data=rim_data)
    bpy.context.scene.collection.objects.link(rim_obj)
    rim_obj.location = (0.0, 1.8, 1.8)


def render(output_path: str, size: int):
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.film_transparent = True
    scene.render.resolution_x = size
    scene.render.resolution_y = size
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.filepath = output_path
    bpy.ops.render.render(write_still=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--size", type=int, default=512)
    parser.add_argument("--target-size", type=float, default=1.2)
    parser.add_argument("--yaw-deg", type=float, default=0.0)
    parser.add_argument("--offset-x", type=float, default=0.0)
    parser.add_argument("--offset-y", type=float, default=0.0)
    parser.add_argument("--offset-z", type=float, default=0.0)
    parser.add_argument("--cam-distance", type=float, default=3.2)
    parser.add_argument("--cam-height", type=float, default=1.5)
    parser.add_argument("--cam-pitch-deg", type=float, default=78.0)
    parser.add_argument("--lens", type=float, default=50.0)
    argv = sys.argv
    if "--" in argv:
        argv = argv[argv.index("--") + 1 :]
    else:
        argv = []
    args = parser.parse_args(argv)

    clear_scene()
    meshes = import_glb(args.model)
    center_and_scale(meshes, args.target_size)
    apply_model_transform(
        meshes,
        yaw_deg=args.yaw_deg,
        offset_x=args.offset_x,
        offset_y=args.offset_y,
        offset_z=args.offset_z,
    )
    setup_camera_and_light(
        cam_distance=args.cam_distance,
        cam_height=args.cam_height,
        cam_pitch_deg=args.cam_pitch_deg,
        lens=args.lens,
    )
    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    render(args.output, args.size)
    print(f"Rendered thumbnail: {args.output}")


if __name__ == "__main__":
    main()

