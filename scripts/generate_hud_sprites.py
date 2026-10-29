"""Pixel-art sprites for the magazine HUD (GUI atlas: assets/tinkers_crossbow/textures/gui/sprites/hud/magazine).
Bolts point right and stack in a vertical magazine; a 1px dark outline keeps them readable on sky, grass and snow.
Colours follow the 连弩1 palette. Requires Python 3 + Pillow.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/tinkers_crossbow/textures/gui/sprites/hud/magazine'
OUT.mkdir(parents=True, exist_ok=True)

# f fletching, n nock, s shaft, h head flank, H head, t tip
BOLT = ['ff......h.',
        '.nssssssHt',
        'ff......h.']
OUTLINE = '#000000aa'
SOLID = 'fnshHt'
SPRITES = {
    'bolt': ({'f': '#d6d6d6', 'n': '#473a2e', 's': '#8b7056', 'h': '#828282', 'H': '#a7a7a7', 't': '#e0e0e0'}, True),
    'bolt_spectral': ({'f': '#f6d75a', 'n': '#473a2e', 's': '#8b7056', 'h': '#c98d1d', 'H': '#e8b830', 't': '#fff1a8'}, True),
    # White fletching, tinted with the potion colour of tipped arrows.
    'bolt_fletching': ({'f': '#ffffff'}, False),
    'slot': ({c: '#0000006e' for c in SOLID}, True),
    'slot_unavailable': ({c: '#b42828c8' for c in SOLID}, True),
    # Drawn over an empty slot, cut to the reload progress.
    'progress': ({c: '#f5d98ce6' for c in SOLID}, False),
}

def rgba(value):
    value = value.removeprefix('#')
    if len(value) == 6: value += 'ff'
    return tuple(int(value[i:i + 2], 16) for i in range(0, 8, 2))

inner = {(x + 1, y + 1): key for y, row in enumerate(BOLT) for x, key in enumerate(row) if key != '.'}
size = (len(BOLT[0]) + 2, len(BOLT) + 2)
for name, (palette, outlined) in SPRITES.items():
    image = Image.new('RGBA', size)
    for (x, y), key in inner.items():
        if key in palette: image.putpixel((x, y), rgba(palette[key]))
    if outlined:
        for y in range(size[1]):
            for x in range(size[0]):
                if (x, y) not in inner and any(n in inner for n in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))):
                    image.putpixel((x, y), rgba(OUTLINE))
    image.save(OUT / (name + '.png'))
print('Wrote', len(SPRITES), 'HUD sprites to', OUT.relative_to(ROOT))
