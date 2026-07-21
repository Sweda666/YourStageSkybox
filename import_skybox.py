#!/usr/bin/env python
"""
Skybox Import Tool — 将各种天空盒格式转换为 panorama_0~5.png 供 YourStageSkybox 使用

用法：
  python import_skybox.py <输入目录> <输出名称> [--size 512]

支持格式：
  1. Humus 格式 (negx/posx/negy/posy/negz/posz .jpg/.png/.bmp)
  2. 十字立方体 Cross-Cubemap (4:3 单图，6面拼合)
  3. 独立六面 (任意命名，按顺序指定)

示例：
  # 从 Humus 城市天空盒导入
  python import_skybox.py ./urban_example/Medborgarplatsen city_night

  # 从十字立方体图片导入
  python import_skybox.py ./skybox_cross.png alien_mars

  # 手动指定六面文件
  python import_skybox.py front.jpg back.jpg left.jpg right.jpg up.jpg down.jpg my_sky
"""

import os, sys, glob
from PIL import Image


# ---- 面命名映射 ----
# panorama_0=北(-Z), 1=东(+X), 2=南(+Z), 3=西(-X), 4=上(+Y), 5=下(-Y)
# Humus: negz=north, posx=east, posz=south, negx=west, posy=up, negy=down
HUMUS_MAP = {
    'negz': 0,    # north
    'posx': 1,    # east
    'posz': 2,    # south
    'negx': 3,    # west
    'posy': 4,    # up
    'negy': 5,    # down
}

# 十字立方体布局 (每面 512px, 总图 2048×1536):
# 行0 (y=0..511):            [上]
# 行1 (y=512..1023):  [左] [前] [右] [后]
# 行2 (y=1024..1535):        [下]
CROSS_FACE_COORDS = {
    # face_index: (x_start, y_start)
    4: (512, 0),      # up
    3: (0, 512),      # west (left)
    2: (512, 512),    # south (front)
    1: (1024, 512),   # east (right)
    0: (1536, 512),   # north (back)
    5: (512, 1024),   # down
}
CROSS_FACE_SIZE = 512


def detect_format(paths):
    """检测输入格式: 'humus', 'cross', 'individual', 'unknown'"""
    if len(paths) == 1 and os.path.isfile(paths[0]) and not paths[0].lower().endswith('.zip'):
        # 单文件 → 十字立方体
        return 'cross'
    if len(paths) >= 6:
        names = {os.path.splitext(os.path.basename(p).lower())[0] for p in paths[:6]}
        humus_keys = {'negx', 'posx', 'negy', 'posy', 'negz', 'posz'}
        if names & humus_keys:
            return 'humus'
        return 'individual'
    return 'unknown'


def find_humus_files(dir_path):
    """在目录中查找 Humus 命名文件"""
    files = {}
    for key in HUMUS_MAP:
        for ext in ['jpg', 'jpeg', 'png', 'bmp', 'tga']:
            path = os.path.join(dir_path, f'{key}.{ext}')
            if os.path.isfile(path):
                files[key] = path
                break
    if len(files) == 6:
        return files
    return None


def convert_humus(dir_path, output_dir, target_size=None):
    """处理 Humus 格式目录"""
    files = find_humus_files(dir_path)
    if not files:
        print(f"  未找到完整 Humus 文件 (需要 negx/posx/negy/posy/negz/posz)")
        return False

    for humus_key, face_idx in HUMUS_MAP.items():
        if humus_key not in files:
            print(f"  缺少: {humus_key}")
            return False
        img = Image.open(files[humus_key]).convert('RGB')
        if target_size:
            img = img.resize((target_size, target_size), Image.Resampling.LANCZOS)
        img.save(os.path.join(output_dir, f'panorama_{face_idx}.png'))
    return True


def convert_cross(image_path, output_dir, target_size=None):
    """处理十字立方体单图"""
    img = Image.open(image_path).convert('RGB')
    w, h = img.size
    face_sz = target_size or CROSS_FACE_SIZE

    # 根据原图尺寸计算每面大小
    if w >= 2048:
        divisor = w // 4
    else:
        divisor = min(w // 4, h // 3) if h >= w else w // 4

    coords = {k: (int(x / (2048 / w)), int(y / (1536 / h))) for k, (x, y) in CROSS_FACE_COORDS.items()}

    for face_idx, (cx, cy) in coords.items():
        face = img.crop((cx, cy, cx + divisor, cy + divisor))
        if face_sz != divisor:
            face = face.resize((face_sz, face_sz), Image.Resampling.LANCZOS)
        face.save(os.path.join(output_dir, f'panorama_{face_idx}.png'))
    return True


def convert_individual(paths, output_dir, target_size=None):
    """处理独立六面（按输入顺序：north, east, south, west, up, down）"""
    for face_idx, path in enumerate(paths[:6]):
        img = Image.open(path).convert('RGB')
        if target_size:
            img = img.resize((target_size, target_size), Image.Resampling.LANCZOS)
        img.save(os.path.join(output_dir, f'panorama_{face_idx}.png'))
    return True


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return

    output_name = args[-1]
    input_args = args[:-1]

    if not input_args:
        print("请提供输入文件/目录")
        return

    # 输出路径
    output_dir = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        'src', 'main', 'resources', 'assets', 'yourstageskybox', 'textures', 'skyboxes', output_name
    )
    os.makedirs(output_dir, exist_ok=True)

    # 自动检测格式
    fmt = detect_format(input_args)

    if fmt == 'humus' or (len(input_args) == 1 and os.path.isdir(input_args[0])):
        # 目录 → 尝试 Humus 格式
        src_dir = input_args[0] if os.path.isdir(input_args[0]) else os.path.dirname(input_args[0])
        print(f"处理 Humus 格式: {src_dir}")
        ok = convert_humus(src_dir, output_dir)
    elif fmt == 'cross':
        print(f"处理十字立方体: {input_args[0]}")
        ok = convert_cross(input_args[0], output_dir)
    elif fmt == 'individual':
        print(f"处理独立六面 ({len(input_args)} 文件)")
        ok = convert_individual(input_args, output_dir)
    else:
        print(f"无法识别格式: {input_args}")
        return

    if ok:
        print(f"\n已输出到: {output_dir}")
        print(f"天空盒名称: {output_name}")
        print(f"命令: /yourstageskybox set {output_name}")
    else:
        print("转换失败")


if __name__ == '__main__':
    main()
