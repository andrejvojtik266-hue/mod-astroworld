#!/usr/bin/env python3
"""Generates the mod's PNG textures (stylised pixel art; no copyrighted artwork).
Run:  python3 tools/generate_assets.py     (needs Pillow)
Part rectangles must match GiantModel.java (128x128 atlas, Minecraft-style box unwrap)."""
from PIL import Image, ImageDraw
import random, os
random.seed(7)
OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/astroworld/textures")

SKIN=(122,78,56); SKIN_D=(100,62,44)
HAIR=(22,16,14); JACKET=(176,40,22); JACKET_D=(120,24,14); JACKET_L=(214,84,40)
PANTS=(28,28,34); BOOT=(210,200,185); BOOT_SOLE=(40,40,40)
GLOW=(255,255,255)

base = Image.new("RGBA",(128,128),(0,0,0,0))
glow = Image.new("RGBA",(128,128),(0,0,0,0))
B = ImageDraw.Draw(base); G = ImageDraw.Draw(glow)

def regions(u,v,w,h,d):
    return {"top":(u+d,v,w,d),"bottom":(u+d+w,v,w,d),"right":(u,v+d,d,h),
            "front":(u+d,v+d,w,h),"left":(u+d+w,v+d,d,h),"back":(u+2*d+w,v+d,w,h)}

def fill(name, part, color, noise=10):
    x,y,w,h = part[name]
    for i in range(w):
        for j in range(h):
            n = random.randint(-noise,noise)
            B.point((x+i,y+j), tuple(max(0,min(255,c+n)) for c in color)+(255,))

def fill_all(part, color, noise=10):
    for k in part: fill(k, part, color, noise)

def gl(part, name, rect, color=GLOW):
    x,y,w,h = part[name]; rx,ry,rw,rh = rect
    G.rectangle([x+rx,y+ry,x+rx+rw-1,y+ry+rh-1], fill=color+(255,))

# ---- HEAD 0,0 8x8x8 : skin, hair line, face
head = regions(0,0,8,8,8); fill_all(head, SKIN)
fill("top", head, HAIR); fill("back", head, HAIR, 4)
x,y,w,h = head["front"]
B.rectangle([x,y,x+7,y+1], fill=HAIR+(255,))              # hairline
B.rectangle([x+2,y+6,x+5,y+6], fill=(70,35,30,255))      # mouth
B.point((x+3,y+4), fill=SKIN_D+(255,)); B.point((x+4,y+4), fill=SKIN_D+(255,))   # nose
B.rectangle([x+3,y+7,x+4,y+7], fill=HAIR+(255,))          # goatee
for k in ("left","right"):
    xx,yy,ww,hh = head[k]; B.rectangle([xx,yy,xx+ww-1,yy+1], fill=HAIR+(255,))
# ---- HAIR cap 0,16 9x3x9 and back 36,16 9x6x2
fill_all(regions(0,16,9,3,9), HAIR, 6); fill_all(regions(36,16,9,6,2), HAIR, 6)
# ---- SHADES 0,28 8x2x1 : dark lens with glowing strip
sh = regions(0,28,8,2,1); fill_all(sh,(10,10,12),2)
gl(sh,"front",(0,0,8,2),(255,255,255))
B.rectangle([sh["front"][0], sh["front"][1], sh["front"][0]+7, sh["front"][1]+1], fill=(15,15,20,255))
# ---- BODY 0,32 8x12x4 : red puffer jacket w/ glowing seams
body = regions(0,32,8,12,4); fill_all(body, JACKET, 12)
for k in ("front","back"):
    x,y,w,h = body[k]
    for r in (3,6,9):                                    # puffer quilting lines
        B.rectangle([x,y+r,x+7,y+r], fill=JACKET_D+(255,))
    B.rectangle([x+3,y,x+4,y+11], fill=(30,30,36,255))  # zipper strip
gl(body,"front",(3,0,2,12),(255,170,120))
gl(body,"front",(0,10,8,1),(255,255,255))
gl(body,"back",(0,10,8,1),(255,255,255))
# ---- ARM 32,32 4x12x4 : sleeve, skin hand
arm = regions(32,32,4,12,4); fill_all(arm, JACKET, 12)
for k in ("front","back","left","right"):
    x,y,w,h = arm[k]
    B.rectangle([x,y+10,x+w-1,y+11], fill=SKIN+(255,))
    B.rectangle([x,y+8,x+w-1,y+8], fill=JACKET_D+(255,))
    gl(arm,k,(0,7,w,1),(255,255,255))
# ---- LEG 0,64 4x12x4 : dark pants, glowing side stripe
leg = regions(0,64,4,12,4); fill_all(leg, PANTS, 6)
for k in ("left","right"): gl(leg,k,(1,0,2,12),(255,120,60))
gl(leg,"front",(0,5,4,1),(255,255,255))
# ---- BOOT 24,64 5x3x6 : chunky boots, glowing sole
boot = regions(24,64,5,3,6); fill_all(boot, BOOT, 8)
for k in ("front","back","left","right"):
    x,y,w,h = boot[k]; B.rectangle([x,y+2,x+w-1,y+2], fill=BOOT_SOLE+(255,)); gl(boot,k,(0,2,w,1),(255,255,255))
# ---- BRAID 64,0 2x10x2 : dark braid with glowing bead tip
br = regions(64,0,2,10,2); fill_all(br, HAIR, 5)
for k in ("front","back","left","right"):
    x,y,w,h = br[k]
    for r in range(0,10,2): B.rectangle([x,y+r,x+w-1,y+r], fill=(40,30,26,255))
    gl(br,k,(0,9,w,1),(255,255,255))
# ---- PENDANT 64,16 3x3x1 : glowing chain pendant
pd = regions(64,16,3,3,1); fill_all(pd,(220,190,70),4)
for k in pd: gl(pd,k,(0,0,pd[k][2],pd[k][3]),(255,230,140))
# ---- SHOULDER PAD 80,0 5x2x5 → used as 6x2x6 : dark with glowing edges
pad = regions(80,0,5,2,5); fill_all(pad,(25,25,30),4)
for k in ("front","back","left","right"): gl(pad,k,(0,0,pad[k][2],1),(255,255,255))
gl(pad,"top",(1,1,3,3),(255,140,80))

base.save(os.path.join(OUT,"entity","giant.png")); glow.save(os.path.join(OUT,"entity","giant_glow.png"))

# ---- ITEM 16x16: futuristic remote (dark body, red screen, glowing buttons, antenna)
it = Image.new("RGBA",(16,16),(0,0,0,0)); D = ImageDraw.Draw(it)
D.rectangle([5,3,10,14], fill=(34,34,42,255), outline=(12,12,16,255))
D.rectangle([6,4,9,7], fill=(255,60,30,255))                       # screen
D.point((7,5), fill=(255,230,150,255)); D.point((8,6), fill=(255,230,150,255))
D.rectangle([6,9,7,10], fill=(255,150,0,255)); D.rectangle([8,9,9,10], fill=(170,60,255,255))
D.rectangle([6,12,9,12], fill=(255,255,255,255))
D.line([8,3,8,0], fill=(200,200,210,255)); D.point((8,0), fill=(255,60,30,255))
D.line([5,5,5,12], fill=(80,80,96,255))
it.save(os.path.join(OUT,"item","astroworld_remote.png"))

# ---- mod icon
ic = Image.new("RGBA",(128,128),(18,6,24,255)); I = ImageDraw.Draw(ic)
for y in range(128):
    I.line([0,y,127,y], fill=(int(40+120*y/128), int(8+30*y/128), int(60-30*y/128),255))
I.ellipse([34,20,94,80], outline=(255,200,120,255), width=3)
I.rectangle([52,50,76,120], fill=(10,8,12,255)); I.rectangle([46,56,82,84], fill=(176,40,22,255))
I.rectangle([56,36,72,52], fill=(122,78,56,255)); I.rectangle([54,32,74,40], fill=(22,16,14,255))
os.makedirs(os.path.join(OUT,".."), exist_ok=True)
ic.save(os.path.join(OUT,"..","icon.png"))
print("textures written")
