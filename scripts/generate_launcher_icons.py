"""Generate WVC's vector adaptive icons and legacy PNGs. Requires Pillow."""
from pathlib import Path
import math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
HEADER = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
# Artwork stays inside the central 66dp adaptive-icon safe zone.
def circle(cx, cy, r):
    return f'M{cx-r},{cy}a{r},{r} 0,1 0,{2*r},0a{r},{r} 0,1 0,{-2*r},0'

layers = []
for radius, color in [(32,'#F0F1F3'),(31,'#E9ECEF'),(30,'#E2E6EA'),(29,'#DBE0E5')]:
    layers.append((54.8,56,radius,color))
layers += [(53,52,29.4,'#FFFFFF'), (54,54,28,'#FAFBFC')]
paths = [f'    <path android:fillColor="{c}" android:pathData="{circle(x,y,r)}" />' for x,y,r,c in layers]
# Two broad Wi-Fi arcs and a dot: legible even at notification-sized previews.
arcs = [(36,45,54,30,72,45),(42,53,54,43,66,53)]
mark = [f'    <path android:fillColor="@android:color/transparent" android:strokeColor="#303539" android:strokeWidth="4.5" android:strokeLineCap="round" android:pathData="M{x},{y}Q{cx},{cy} {ex},{ey}" />' for x,y,cx,cy,ex,ey in arcs]
mark.append(f'    <path android:fillColor="#303539" android:pathData="{circle(54,63,3)}" />')
(RES/'drawable/ic_launcher_foreground.xml').write_text(HEADER+'\n'.join(paths+mark)+'\n</vector>\n')
(RES/'drawable/ic_launcher_monochrome.xml').write_text(HEADER+'\n'.join(mark)+'\n</vector>\n')
(RES/'drawable/ic_launcher_background.xml').write_text(HEADER+'    <path android:fillColor="#F5F6F7" android:pathData="M0,0h108v108H0z" />\n</vector>\n')
for version in (26,33):
    folder = RES/f'mipmap-anydpi-v{version}'
    folder.mkdir(exist_ok=True)
    for name in ('ic_launcher','ic_launcher_round'):
        mono = '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n' if version == 33 else ''
        (folder/f'{name}.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@drawable/ic_launcher_background" />\n    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'+mono+'</adaptive-icon>\n')

def render(size, rounded=False):
    scale=8
    n=size*scale
    image=Image.new('RGBA',(n,n),'#F5F6F7')
    d=ImageDraw.Draw(image)
    # Legacy icons render the same 72dp launcher-visible crop of the 108dp layers.
    def pt(x,y): return ((x-18)*n/72,(y-18)*n/72)
    for x,y,r,c in layers:
        d.ellipse([pt(x-r,y-r),pt(x+r,y+r)],fill=c)
    for x,y,cx,cy,ex,ey in arcs:
        points=[pt((1-t)**2*x+2*(1-t)*t*cx+t*t*ex,(1-t)**2*y+2*(1-t)*t*cy+t*t*ey) for t in [i/100 for i in range(101)]]
        width=round(4.5*n/72)
        edges = [[], []]
        for i, (px, py) in enumerate(points):
            a, b = points[max(0,i-1)], points[min(100,i+1)]
            dx,dy=b[0]-a[0],b[1]-a[1]
            length=math.hypot(dx,dy)
            for side,sign in enumerate((-1,1)):
                edges[side].append((px-sign*dy/length*width/2,py+sign*dx/length*width/2))
        d.polygon(edges[0]+edges[1][::-1],fill='#303539')
        for px,py in (points[0],points[-1]):
            d.ellipse((px-width/2,py-width/2,px+width/2,py+width/2),fill='#303539')
    d.ellipse([pt(51,60),pt(57,66)],fill='#303539')
    if rounded:
        mask=Image.new('L',(n,n)); ImageDraw.Draw(mask).ellipse((0,0,n-1,n-1),fill=255); image.putalpha(mask)
    return image.resize((size,size),Image.Resampling.LANCZOS)
for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    folder=RES/f'mipmap-{density}'
    for old in folder.glob('ic_launcher*.webp'): old.unlink()
    render(size).save(folder/'ic_launcher.png')
    render(size,True).save(folder/'ic_launcher_round.png')
render(512).convert('RGB').save(ROOT/'app/src/main/ic_launcher-playstore.png')
render(512).save(ROOT/'docs/screenshots/launcher-icon.png')
