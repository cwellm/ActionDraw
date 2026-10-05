"""Cut the static font files ActionDraw ships from the variable fonts beside this script.

Compose Desktop 1.7 loads a font file as it is: it cannot set a variable font's axes, so a
variable font would only ever show its default instance. The weights the app uses are cut here
once, with fontTools, and the static files go into src/main/resources/fonts/.

    pip install fonttools
    python art/fonts/make-instances.py

The variable sources are from github.com/google/fonts (ofl/bricolagegrotesque, ofl/caveat),
under the SIL Open Font License 1.1 (OFL-*.txt beside them, copied next to the outputs).
"""
import shutil
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

HERE = Path(__file__).resolve().parent
OUT = HERE.parent.parent / "src" / "main" / "resources" / "fonts"

# (source, family, style, axis positions). Text sizes use opsz 14; the display cut is for large
# titles only, where a high optical size tightens the spacing and sharpens the contrast.
INSTANCES = [
    ("BricolageGrotesque-Variable.ttf", "Bricolage Grotesque", "Regular", {"opsz": 14, "wdth": 100, "wght": 400}),
    ("BricolageGrotesque-Variable.ttf", "Bricolage Grotesque", "Medium", {"opsz": 14, "wdth": 100, "wght": 500}),
    ("BricolageGrotesque-Variable.ttf", "Bricolage Grotesque", "SemiBold", {"opsz": 14, "wdth": 100, "wght": 600}),
    ("BricolageGrotesque-Variable.ttf", "Bricolage Grotesque", "Bold", {"opsz": 14, "wdth": 100, "wght": 700}),
    ("BricolageGrotesque-Variable.ttf", "Bricolage Grotesque Display", "ExtraBold", {"opsz": 48, "wdth": 100, "wght": 800}),
    ("Caveat-Variable.ttf", "Caveat", "Medium", {"wght": 500}),
    ("Caveat-Variable.ttf", "Caveat", "Bold", {"wght": 700}),
]


def make_static(font: TTFont, family: str, style: str, weight: int) -> None:
    """Name and flag a cut as the static font it now is.

    A cut keeps the variable font's names and STAT table, and Windows (DirectWrite, which Skia
    uses there) then cannot tell the cuts apart: they came back as weight 1, or one cut's weight
    for another's. The weight is what a font loader matches on, so it has to be right.
    """
    ribbi = style in ("Regular", "Bold")
    ps = family.replace(" ", "") + "-" + style
    names = {
        1: family if ribbi else f"{family} {style}",
        2: style if ribbi else "Regular",
        3: f"{ps};static cut",
        4: f"{family} {style}",
        6: ps,
        16: family,
        17: style,
    }
    table = font["name"]
    for name_id in (1, 2, 3, 4, 6, 16, 17, 25):
        table.removeNames(nameID=name_id)
    for name_id, text in names.items():
        table.setName(text, name_id, 3, 1, 0x409)
    if "STAT" in font:
        del font["STAT"]

    os2 = font["OS/2"]
    os2.usWeightClass = weight
    os2.fsSelection &= ~((1 << 0) | (1 << 5) | (1 << 6))  # italic, bold, regular
    os2.fsSelection |= (1 << 5) if style == "Bold" else (1 << 6) if style == "Regular" else 0
    font["head"].macStyle = 1 if style == "Bold" else 0


OUT.mkdir(parents=True, exist_ok=True)
for source, family, style, axes in INSTANCES:
    font = instantiateVariableFont(TTFont(HERE / source), axes)
    make_static(font, family, style, axes["wght"])
    output = OUT / (family.replace(" ", "").replace("GrotesqueDisplay", "Grotesque-Display") + "-" + style + ".ttf")
    font.save(output)
    print(f"{output.name:44} {axes}  {output.stat().st_size // 1024} KB")
for licence in HERE.glob("OFL-*.txt"):
    shutil.copyfile(licence, OUT / licence.name)
