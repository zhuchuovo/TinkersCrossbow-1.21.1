"""Material sprites for the repeating crossbow, rebuilt from the hand-drawn reference 连弩1.

The part sprites in art/original decide which part owns each pixel (their alpha masks); every pixel takes its colour
from art/reference/repeating_crossbow.png. Reference pixels outside all masks join the neighbouring part whose colour
is closest. Each part has a reference material that reproduces the drawing exactly: wood limb and grip, string
bowstring, stone body and magazine. Other materials move each pixel by the difference between their Tinkers palette and
the reference palette at the same grey, measured in OKLab, so the drawn shading and darkness carry over to every material.
Copper, rose gold, cobalt and manyullyn receivers are drawn like the Tinkers metal parts instead: the drawn lightness, stretched
over the bright part of the palette, picks the colour straight from the material palette, so they keep the metal shine
the dark stone drawing would dull.

The receiver is drawn as one piece (receiver_fixed with its core, receiver). The user's cut pieces receiver_body (机体)
and receiver_magazine (弹舱) split it: each receiver pixel belongs to the piece that drew it, the core pixels of each
piece follow that piece's material and the rest keep the drawn colours. The pieces themselves, centred as drawn, are
the part item icons, the part builder and station slot patterns, and the holes of their gold, sand and red sand casts.
The receiver item (机匣) reuses the crossbow layers through the tool model, so only its slot pattern is generated here.
Requires Python 3 + Pillow. Run from the project root; --tconstruct locates the local Tinkers' Construct checkout.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import argparse
import copy
import json
import math
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--tconstruct', type=Path, default=Path('../TinkersConstruct-1.21.1'))
parser.add_argument('--minecraft', type=Path, help='jar with vanilla assets, sampled by the wool and ice palettes')
parser.add_argument('--out', type=Path, help='resource root to write into (default src/main/resources)')
parser.add_argument('--preview', type=Path, help='preview sheet path (default docs/texture-preview.png)')
args = parser.parse_args()
ROOT = Path(__file__).resolve().parents[1]
RESOURCES = args.out or ROOT / 'src/main/resources'
TOOL = RESOURCES / 'assets/tinkers_crossbow/textures/item/tool/repeating_crossbow'
PARTS = RESOURCES / 'assets/tinkers_crossbow/textures/item/tool/parts'
PATTERNS = RESOURCES / 'assets/tinkers_crossbow/textures/gui/tinker_pattern'
ITEMS = RESOURCES / 'assets/tinkers_crossbow/textures/item'
CASTS = ['cast', 'sand_cast', 'red_sand_cast']
PREVIEW = args.preview or ROOT / 'docs/texture-preview.png'
ROOFS = [args.tconstruct / 'src/generated/resources', args.tconstruct / 'src/main/resources']
RECEIVERS = {'rock', 'flint', 'bone', 'copper', 'iron', 'rose_gold', 'amethyst_bronze', 'cobalt', 'manyullyn'}
# Masks in art/original. receiver_fixed keeps the drawn colours; receiver is the core that follows the materials.
MASKS = ['limb', 'grip', 'bowstring', 'receiver_fixed', 'receiver']
# Receiver pieces drawn by the user, also the part items: layer name -> item name.
PIECES = {'body': 'receiver_body', 'magazine': 'receiver_magazine'}
# Model draw order. receiver_fixed keeps the drawn colours, the other layers follow their material.
LAYERS = ['limb', 'grip', 'bowstring', 'receiver_fixed', 'body', 'magazine']
STATS = {'limb': 'tconstruct:limb', 'grip': 'tconstruct:grip', 'bowstring': 'tconstruct:bowstring'}
# Receiver metals shaded from their own palette: drawn lightness -> palette grey from METAL_GREY[0] to [1], curve [2].
METALS = {'copper', 'rose_gold', 'cobalt', 'manyullyn'}
METAL_GREY = 85, 235, 0.75
REFERENCE = {'limb': 'tconstruct:wood', 'grip': 'tconstruct:wood', 'bowstring': 'tconstruct:string',
             'body': 'tconstruct:rock/stone', 'magazine': 'tconstruct:rock/stone'}

minecraft = args.minecraft or next((ROOT / 'build/moddev/artifacts').glob('*client-extra*.jar'), None)
vanilla = zipfile.ZipFile(minecraft) if minecraft and Path(minecraft).exists() else None
if vanilla is None: print('No vanilla asset jar; wool and ice palettes fall back to their plain colours.')

raw = {}
for roof in ROOFS:
    folder = roof / 'assets/tconstruct/tinkering/materials'
    for path in folder.rglob('*.json'):
        raw['tconstruct:' + path.relative_to(folder).with_suffix('').as_posix()] = json.loads(path.read_text(encoding='utf-8'))

def merge(a, b):
    result = copy.deepcopy(a)
    for k, v in b.items():
        result[k] = merge(result[k], v) if isinstance(v, dict) and isinstance(result.get(k), dict) else copy.deepcopy(v)
    return result

def resolve(name, visited=frozenset()):
    if name in visited: return {}
    value = raw.get(name, {})
    parent = value.get('parent')
    return merge(resolve(parent, visited | {name}) if parent else {}, value)

def texture_name(name):
    return resolve(name).get('texture', name.replace('/', '_'))

cache = {}
def texture(name):
    if name not in cache:
        namespace, path = name.split(':', 1)
        cache[name] = None
        for roof in reversed(ROOFS):
            file = roof / 'assets' / namespace / 'textures' / (path + '.png')
            if file.exists():
                cache[name] = Image.open(file).convert('RGBA')
                break
        entry = f'assets/{namespace}/textures/{path}.png'
        if cache[name] is None and vanilla is not None and entry in vanilla.namelist():
            with vanilla.open(entry) as file:
                cache[name] = Image.open(file).convert('RGBA')
    return cache[name]

def argb(value):
    value = str(value).removeprefix('#')
    if len(value) == 6: value = 'FF' + value
    return tuple(int(value[i:i + 2], 16) for i in (2, 4, 6, 0))

class Palette:
    """A Tinkers grey_to_color or grey_to_sprite palette, sampled like the Tinkers sprite generator does."""
    def __init__(self, entries):
        if isinstance(entries, dict): entries = [{'grey': int(k), 'color': v} for k, v in entries.items()]
        self.entries = sorted(entries, key=lambda entry: entry['grey'])
        self.textured = any('path' in entry for entry in self.entries)

    def entry(self, entry, x, y, frame):
        color = argb(entry['color']) if 'color' in entry else None
        image = texture(entry['path']) if 'path' in entry else None
        if image is None: return color or (255, 255, 255, 255)
        # The art is 16x16 drawn at 2x, so one texel covers one drawn pixel.
        x, y, width = x // 2, y // 2, image.width
        pixel = image.getpixel((x % width, y % image.height) if frame < 0 else (x % width, (y % width + frame * width) % image.height))
        return pixel if color is None else tuple(c * p // 255 for c, p in zip(color, pixel))

    def color(self, grey, x=0, y=0, frame=-1):
        entries = self.entries
        if grey <= entries[0]['grey']: return self.entry(entries[0], x, y, frame)
        for low, high in zip(entries, entries[1:]):
            if grey < high['grey']:
                if grey == low['grey']: return self.entry(low, x, y, frame)
                first, second = self.entry(low, x, y, frame), self.entry(high, x, y, frame)
                diff, divisor = grey - low['grey'], high['grey'] - low['grey']
                return tuple(a + int((b - a) * diff / divisor) for a, b in zip(first, second))
        return self.entry(entries[-1], x, y, frame)

def frames_of(transformer):
    """Palette and sprite frame used for each animation frame of a Tinkers sprite transformer."""
    kind = transformer.get('type')
    if kind == 'tconstruct:recolor_sprite': return [(Palette(transformer['color_mapping']['palette']), -1)]
    if kind == 'tconstruct:grey_to_sprite': return [(Palette(transformer['palette']), -1)]
    if kind == 'tconstruct:animated_sprite':
        palette = Palette(transformer['palette'])
        return [(palette, frame) for frame in range(int(transformer.get('frames', 1)))]
    if kind == 'tconstruct:frames': return [frames_of(frame)[0] for frame in transformer['frames']]
    return None

def metadata(transformer, frames):
    if frames <= 1: return None
    if transformer.get('meta'):
        namespace, name = transformer['meta'].split(':', 1)
        for roof in reversed(ROOFS):
            file = roof / 'assets' / namespace / 'textures' / (name + '.png.mcmeta')
            if file.exists():
                result = json.loads(file.read_text(encoding='utf-8'))
                animation = result.setdefault('animation', {})
                for key in ('width', 'height'):
                    if key in animation: animation[key] = 32
                return result
    return {'animation': {'frametime': 4}}

def linear(c):
    c /= 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

def srgb(c):
    c = min(1.0, max(0.0, c))
    return round(255 * (12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055))

def oklab(color):
    r, g, b = (linear(c) for c in color[:3])
    l = (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b) ** (1 / 3)
    m = (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b) ** (1 / 3)
    s = (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b) ** (1 / 3)
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)

def from_oklab(lab):
    L, a, b = lab
    l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    return (srgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
            srgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
            srgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s))

def transfer(drawn, reference, material):
    """Lightness moves by material - reference. Where the reference palette is coloured, the material's colour is
    rotated and scaled like the drawing differs from that palette (keeping the drawing's muted saturation);
    near-grey references add the difference instead. Both give the drawn colour back for the reference material."""
    lightness = drawn[0] + material[0] - reference[0]
    added = complex(drawn[1] + material[1] - reference[1], drawn[2] + material[2] - reference[2])
    chroma = math.hypot(reference[1], reference[2])
    if chroma < 1e-4: return lightness, added.real, added.imag
    ratio = complex(drawn[1], drawn[2]) / complex(reference[1], reference[2])
    if abs(ratio) > 1.25: ratio *= 1.25 / abs(ratio)
    scaled = complex(material[1], material[2]) * ratio
    weight = min(1, max(0, (chroma - 0.015) / 0.025))
    weight = weight * weight * (3 - 2 * weight)
    chromatic = scaled * weight + added * (1 - weight)
    return lightness, chromatic.real, chromatic.imag

# Build the layers: masks from the supplied part sprites, colours from the reference drawing.
reference = Image.open(ROOT / 'art/reference/repeating_crossbow.png').convert('RGBA')
size = reference.size
masks = {name: Image.open(ROOT / 'art/original' / (name + '.png')).convert('RGBA').getchannel('A') for name in MASKS}
owners = {(x, y): [name for name in MASKS if masks[name].getpixel((x, y))] for y in range(size[1]) for x in range(size[0])}
masked = {pixel: names[:] for pixel, names in owners.items()}
for (x, y), names in owners.items():
    color = reference.getpixel((x, y))
    if names or not color[3]: continue
    near = [(sum((a - b) ** 2 for a, b in zip(color, reference.getpixel(n))), abs(n[0] - x) + abs(n[1] - y), n)
            for n in ((nx, ny) for ny in range(y - 2, y + 3) for nx in range(x - 2, x + 3)) if masked.get(n)]
    names.append(masked[min(near)[2]][0])
def pixels(image):
    return {(x, y): image.getpixel((x, y)) for y in range(image.height) for x in range(image.width) if image.getpixel((x, y))[3]}

# Place each cut piece where it matches the drawn receiver best; the pieces are centred in their own canvas.
original = pixels(Image.open(ROOT / 'art/original/receiver_fixed.png').convert('RGBA'))
pieces, offsets = {}, {}
for name, item_name in PIECES.items():
    pieces[name] = pixels(Image.open(ROOT / 'art/original' / (item_name + '.png')).convert('RGBA'))
    score, dx, dy = max((sum(original.get((x + dx, y + dy)) == c for (x, y), c in pieces[name].items()), dx, dy)
                        for dy in range(-size[1] // 2, size[1] // 2 + 1, 2) for dx in range(-size[0] // 2, size[0] // 2 + 1, 2))
    assert score * 4 >= len(pieces[name]) * 3, f'{item_name} does not match the drawn receiver'
    offsets[name] = dx, dy
placed = {name: {(x + offsets[name][0], y + offsets[name][1]): c for (x, y), c in piece.items()} for name, piece in pieces.items()}
# Where both pieces drew a pixel, the one that kept the original colour owns it (the other redrew its cut edge there).
receiver_pixels = [pixel for pixel, names in owners.items() if 'receiver_fixed' in names or 'receiver' in names]
side = {}
for pixel in receiver_pixels:
    names = [name for name in PIECES if pixel in placed[name]]
    if len(names) > 1: names = [name for name in names if placed[name][pixel] == original.get(pixel)] or names
    if len(names) == 1: side[pixel] = names[0]
def nearest_side(pixel):
    """Unclaimed or doubly claimed pixels follow their closest uniquely owned neighbours."""
    x, y = pixel
    near = [(abs(nx - x) + abs(ny - y), name) for (nx, ny), name in side.items() if 0 < abs(nx - x) + abs(ny - y) <= 4]
    votes = {name: sum(1 / d for d, n in near if n == name) for name in PIECES}
    return max(PIECES, key=lambda name: votes[name])
for pixel in receiver_pixels:
    if pixel not in side: side[pixel] = nearest_side(pixel)

layers = {name: Image.new('RGBA', size) for name in LAYERS}
for (x, y), names in owners.items():
    for name in names:
        layers[side[(x, y)] if name == 'receiver' else name].putpixel((x, y), reference.getpixel((x, y)))
drawn = Image.new('RGBA', size)
for name in LAYERS: drawn.alpha_composite(layers[name])
assert drawn.tobytes() == reference.tobytes(), 'layers must recreate the reference drawing'

class Part:
    def __init__(self, name):
        self.name, self.image = name, layers[name]
        frames = frames_of(resolve(REFERENCE[name])['generator']['transformer'])
        assert len(frames) == 1 and not frames[0][0].textured
        self.palette = frames[0][0]
        self.curve = [oklab(self.palette.color(grey)) for grey in range(256)]
        self.pixels = {}
        for y in range(size[1]):
            for x in range(size[0]):
                color = self.image.getpixel((x, y))
                if not color[3]: continue
                lab = oklab(color)
                # Reference palette grey whose lightness matches the drawn pixel.
                grey = min(range(256), key=lambda g: (abs(self.curve[g][0] - lab[0]), g))
                self.pixels[(x, y)] = color, lab, grey

    def grey(self):
        """Grey template for Tinkers' tinted fallback, used by materials without a generated sprite."""
        result = Image.new('RGBA', size)
        for (x, y), (color, _, grey) in self.pixels.items(): result.putpixel((x, y), (grey, grey, grey, color[3]))
        return result

    def silhouette(self):
        """Unknown material: the drawing in grey, like 连弩灰图1."""
        result = Image.new('RGBA', size)
        for (x, y), (color, _, _) in self.pixels.items():
            value = max(color[:3])
            result.putpixel((x, y), (value, value, value, color[3]))
        return result

    def metal(self, frames):
        """Metal receiver piece: the drawn lightness across both pieces, stretched to METAL_GREY, read from the palette."""
        low, high, curve = METAL_GREY
        darkest, lightest = metal_light
        result = Image.new('RGBA', (size[0], size[1] * len(frames)))
        for index, (palette, frame) in enumerate(frames):
            for (x, y), (color, lab, _) in self.pixels.items():
                grey = round(low + (high - low) * ((lab[0] - darkest) / (lightest - darkest)) ** curve)
                material = palette.color(grey, x, y, frame)
                if material[3]: result.putpixel((x, y + index * size[1]), material[:3] + (color[3] * material[3] // 255,))
        return result

    def recolor(self, frames):
        result = Image.new('RGBA', (size[0], size[1] * len(frames)))
        for index, (palette, frame) in enumerate(frames):
            for (x, y), (color, lab, grey) in self.pixels.items():
                material = palette.color(grey, x, y, frame)
                if not material[3]: continue
                rgb = from_oklab(transfer(lab, self.curve[grey], oklab(material)))
                result.putpixel((x, y + index * size[1]), rgb + (color[3] * material[3] // 255,))
        return result

parts = {name: Part(name) for name in ['limb', 'grip', 'bowstring', 'body', 'magazine']}
metal_light = (min(lab[0] for name in PIECES for _, lab, _ in parts[name].pixels.values()),
               max(lab[0] for name in PIECES for _, lab, _ in parts[name].pixels.values()))
fixed = layers['receiver_fixed']

# Clear previous output, then write the untinted layers and grey templates.
TOOL.mkdir(parents=True, exist_ok=True)
PARTS.mkdir(parents=True, exist_ok=True)
PATTERNS.mkdir(parents=True, exist_ok=True)
# 'receiver' also removes the sprites of the former single-material receiver.
generated = set(LAYERS) | set(PIECES.values()) | {'receiver'}
for folder in (TOOL, PARTS):
    for file in folder.iterdir():
        if file.suffix in ('.png', '.mcmeta') and file.name.split('.')[0].split('_tconstruct_')[0] in generated: file.unlink()
fixed.save(TOOL / 'receiver_fixed.png')
for name, part in parts.items(): part.grey().save(TOOL / (name + '.png'))

# Colours of the original receiver art as 连弩1 draws them, for the pixels a piece redrew along its cut.
pairs = {}
for pixel, color in original.items():
    if reference.getpixel(pixel)[3]: pairs.setdefault(color, []).append(reference.getpixel(pixel))
palette_map = {color: max(set(found), key=found.count) for color, found in pairs.items()}
def mapped(color):
    key = color if color in palette_map else min(palette_map, key=lambda c: sum((a - b) ** 2 for a, b in zip(c, color)))
    return palette_map[key]

def piece_item(name, sprite, frames):
    """The piece in the shape the user drew (centred), coloured like the crossbow: its core from the material sprite,
    the rest in the drawn 连弩1 colours."""
    dx, dy = offsets[name]
    result = Image.new('RGBA', (size[0], size[1] * frames))
    for frame in range(frames):
        for (x, y), color in pieces[name].items():
            target = (x + dx, y + dy)
            if side.get(target) == name and layers[name].getpixel(target)[3]:
                value = sprite.getpixel((target[0], target[1] + frame * size[1]))
            elif side.get(target) == name and color == original.get(target):
                value = fixed.getpixel(target)
            else:
                value = mapped(color)
            result.putpixel((x, y + frame * size[1]), value)
    for frame in range(frames):
        drawn_shape = {(x, y) for y in range(size[1]) for x in range(size[0]) if result.getpixel((x, y + frame * size[1]))[3]}
        assert drawn_shape == set(pieces[name]), f'{name} item must keep the drawn shape'
    return result

def pattern(shape):
    """Station slot icon: outline of a drawn shape at the size of the Tinkers patterns."""
    mask = Image.new('L', size)
    for pixel in shape: mask.putpixel(pixel, 255)
    mask = mask.crop(mask.getbbox())
    mask = mask.resize((mask.width // 2, mask.height // 2), Image.Resampling.NEAREST)
    scale = 14 / max(mask.size)
    mask = mask.resize((round(mask.width * scale), round(mask.height * scale)), Image.Resampling.NEAREST)
    inside = lambda x, y: 0 <= x < mask.width and 0 <= y < mask.height and mask.getpixel((x, y))
    result = Image.new('RGBA', (16, 16))
    left, top = (16 - mask.width) // 2, (16 - mask.height) // 2
    for y in range(mask.height):
        for x in range(mask.width):
            if inside(x, y) and not all(inside(x + i, y + j) for i, j in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                result.putpixel((left + x, top + y), (50, 50, 50, 255))
    return result

def cast(kind, shape):
    """Tinkers blank cast with the piece cut out at 16x16; the rim above and left of the hole is in shadow, the rest lit."""
    blank = texture(f'tconstruct:item/{kind}/blank')
    assert blank is not None and blank.size == (16, 16), f'missing Tinkers {kind} blank'
    hole = {(x // 2, y // 2) for x, y in shape}
    shades = sorted({blank.getpixel((x, y)) for y in range(16) for x in range(16) if blank.getpixel((x, y))[3]}, key=lambda c: sum(c[:3]))
    result = blank.copy()
    for pixel in hole: result.putpixel(pixel, (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if (x, y) in hole or not blank.getpixel((x, y))[3]: continue
            score = ((x, y + 1) in hole) + ((x + 1, y) in hole) - ((x, y - 1) in hole) - ((x - 1, y) in hole)
            if score: result.putpixel((x, y), shades[0] if score > 0 else shades[-1])
    return result

for name, item_name in PIECES.items():
    for kind in CASTS:
        (ITEMS / kind).mkdir(parents=True, exist_ok=True)
        cast(kind, pieces[name]).save(ITEMS / kind / (item_name + '.png'))
    piece_item(name, parts[name].grey(), 1).save(PARTS / (item_name + '.png'))
    pattern(pieces[name]).save(PATTERNS / (item_name + '.png'))
pattern(receiver_pixels).save(PATTERNS / 'receiver.png')
written = {}
for name in sorted(raw):
    info = resolve(name)
    generator = info.get('generator')
    if not generator: continue
    tex = texture_name(name)
    if tex is None: continue
    namespace, path = tex.split(':', 1)
    suffix = namespace + '_' + path.replace('/', '_')
    stats = generator.get('supported_stats', generator.get('supportedStats', []))
    base_material = name.split(':', 1)[1].split('/')[0]
    if base_material == 'unknown': continue
    frames = frames_of(generator['transformer'])
    if frames is None:
        print('Skipping', name, 'unsupported transformer', generator['transformer'].get('type'))
        continue
    for part_name, part in parts.items():
        if part_name in PIECES and base_material not in RECEIVERS: continue
        if part_name not in PIECES and STATS[part_name] not in stats: continue
        output = TOOL / (part_name + '_' + suffix + '.png')
        if output in written: continue
        if tex == texture_name(REFERENCE[part_name]): result = layers[part_name].copy()
        elif part_name in PIECES and base_material in METALS: result = part.metal(frames)
        else: result = part.recolor(frames)
        result.save(output)
        meta = metadata(generator['transformer'], len(frames)) if result.height > size[1] else None
        if meta: output.with_suffix('.png.mcmeta').write_text(json.dumps(meta, indent=2) + '\n')
        if part_name in PIECES:
            item = PARTS / (PIECES[part_name] + '_' + suffix + '.png')
            piece_item(part_name, result, result.height // size[1]).save(item)
            if meta: item.with_suffix('.png.mcmeta').write_text(json.dumps(meta, indent=2) + '\n')
        written[output] = result

# The station shows missing parts with the unknown material.
for part_name, part in parts.items():
    part.silhouette().save(TOOL / (part_name + '_tconstruct_unknown.png'))
for name, item_name in PIECES.items():
    piece_item(name, parts[name].silhouette(), 1).save(PARTS / (item_name + '_tconstruct_unknown.png'))

def sprite(part_name, suffix):
    file = TOOL / f'{part_name}_tconstruct_{suffix}.png'
    return Image.open(file).convert('RGBA').crop((0, 0) + size) if file.exists() else None

def crossbow(limb='wood', grip='wood', bowstring='string', body='rock_stone', magazine='rock_stone'):
    image = Image.new('RGBA', size)
    for layer in [sprite('limb', limb), sprite('grip', grip), sprite('bowstring', bowstring), fixed,
                  sprite('body', body), sprite('magazine', magazine)]:
        if layer is not None: image.alpha_composite(layer)
    return image

def receiver(body='rock_stone', magazine='rock_stone'):
    image = fixed.copy()
    for layer in [sprite('body', body), sprite('magazine', magazine)]:
        if layer is not None: image.alpha_composite(layer)
    return image

def item(name, material):
    return Image.open(PARTS / f'{PIECES[name]}_tconstruct_{material}.png').convert('RGBA').crop((0, 0) + size)

assert crossbow().tobytes() == reference.tobytes(), 'default materials must match the reference drawing'
ORDER = ['rock_stone', 'flint', 'bone', 'copper', 'iron', 'rose_gold', 'amethyst_bronze', 'cobalt', 'manyullyn']
rows = [
    ('reference / default', [('reference', reference), ('default', crossbow())]),
    ('body + magazine', [(m, crossbow(body=m, magazine=m)) for m in ORDER]),
    ('body', [(m, crossbow(body=m)) for m in ORDER]),
    ('magazine', [(m, crossbow(magazine=m)) for m in ORDER]),
    ('mixed', [(b + '/' + m, crossbow(body=b, magazine=m)) for b, m in
               [('cobalt', 'rose_gold'), ('iron', 'copper'), ('amethyst_bronze', 'cobalt'), ('rose_gold', 'iron')]]),
    ('body item', [(m, item('body', m)) for m in ORDER + ['unknown']]),
    ('magazine item', [(m, item('magazine', m)) for m in ORDER + ['unknown']]),
    ('receiver item', [(m, receiver(m, m)) for m in ORDER] + [('cobalt/rose_gold', receiver('cobalt', 'rose_gold'))]),
    ('casts', [(f'{item_name} {kind}', Image.open(ITEMS / kind / (item_name + '.png')).convert('RGBA').resize(size, Image.Resampling.NEAREST))
               for item_name in PIECES.values() for kind in CASTS]),
    ('slot patterns', [(item_name, Image.open(PATTERNS / (item_name + '.png')).convert('RGBA').resize(size, Image.Resampling.NEAREST))
                       for item_name in list(PIECES.values()) + ['receiver']]),
    ('limb + grip', [(m, crossbow(limb=m, grip=m)) for m in ['iron', 'copper', 'cobalt', 'bamboo', 'bone', 'manyullyn', 'slimesteel', 'queens_slime', 'fiery']]),
    ('bowstring', [(m, crossbow(bowstring=m)) for m in ['string', 'vine', 'skyslime_vine', 'darkthread', 'leather', 'rose_gold']]),
]
scale, cell = 4, 32 * 4 + 12
sheet = Image.new('RGB', (cell * max(len(r[1]) for r in rows) + 120, len(rows) * (cell + 16) + 8), (52, 56, 64))
draw = ImageDraw.Draw(sheet)
for row, (title, items) in enumerate(rows):
    y = row * (cell + 16) + 8
    draw.text((6, y + cell // 2 - 6), title, fill=(230, 233, 240))
    for column, (label, image) in enumerate(items):
        x = 120 + column * cell
        sheet.paste((139, 139, 139), (x, y, x + 32 * scale, y + 32 * scale))
        big = image.resize((32 * scale, 32 * scale), Image.Resampling.NEAREST)
        sheet.paste(big, (x, y), big)
        draw.text((x, y + 32 * scale + 2), label[:22], fill=(230, 233, 240))
PREVIEW.parent.mkdir(parents=True, exist_ok=True)
sheet.save(PREVIEW)
# Station slot positions over each part of the crossbow icon the station draws behind the slots (3.7x, near 10.65, 20.15).
for name in LAYERS:
    if name == 'receiver_fixed': continue
    points = pixels(layers[name])
    cx, cy = (sum(point[i] for point in points) / len(points) / 2 for i in (0, 1))
    print(f'{name:10} centre {cx:5.2f},{cy:5.2f} -> slot {10.65 + 3.7 * (cx + 0.5) - 8:5.1f},{20.15 + 3.7 * (cy + 0.5) - 8:5.1f}')
print('Generated', len(written), 'material sprites; default materials reproduce the reference drawing.')
