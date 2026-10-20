# Regenerates the README artwork (light + dark): python3 docs/assets/generate.py docs/assets
import sys, os
OUT = sys.argv[1]
FONT = "-apple-system, 'SF Pro Display', 'Segoe UI Variable Display', 'Segoe UI', Inter, system-ui, Helvetica, Arial, sans-serif"
THEMES = {
  "light": dict(bg="#F5F5F7", surface="#FFFFFF", text="#1D1D1F", sec="#6E6E73", line="#D2D2D7",
                teal="#00A88A", blue="#0071E3", amber="#C77700", screen="#F5F5F7", device="#1D1D1F",
                joint="#FFFFFF", shadow="0.10"),
  "dark":  dict(bg="#000000", surface="#1C1C1E", text="#F5F5F7", sec="#86868B", line="#38383A",
                teal="#62E6C5", blue="#2997FF", amber="#FFC857", screen="#0B0B0F", device="#3A3A3C",
                joint="#F5F5F7", shadow="0.6"),
}

OPEN = dict(thumb=[(-28,-30),(-40,-55),(-48,-77)], index=[(-14,-55),(-18,-87),(-20,-111)],
            middle=[(2,-59),(4,-95),(5,-123)], ring=[(16,-55),(22,-87),(26,-109)],
            pinky=[(30,-45),(40,-65),(48,-83)])
FIST = dict(thumb=[(-24,-26),(-28,-42),(-8,-46)], index=[(-14,-55),(-16,-74),(-13,-50)],
            middle=[(2,-59),(2,-79),(2,-52)], ring=[(16,-55),(18,-73),(16,-49)],
            pinky=[(28,-47),(31,-63),(28,-43)])

def hand(cx, cy, s, pose, color, joint, mirror=False, sw=4):
    m = -1 if mirror else 1
    P = lambda p: (cx + m*p[0]*s, cy + p[1]*s)
    out = []
    knuckles = [P(pose[f][0]) for f in ("index","middle","ring","pinky")]
    out.append(f'<polyline points="{" ".join(f"{x:.1f},{y:.1f}" for x,y in knuckles)}" fill="none" stroke="{color}" stroke-opacity="0.45" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round"/>')
    for f, pts in pose.items():
        chain = [P((0,0))] + [P(p) for p in pts]
        out.append(f'<polyline points="{" ".join(f"{x:.1f},{y:.1f}" for x,y in chain)}" fill="none" stroke="{color}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round"/>')
    for f, pts in pose.items():
        for i, p in enumerate(pts):
            x, y = P(p); r = (sw*0.9 if i == len(pts)-1 else sw*0.7)
            out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.1f}" fill="{joint}" stroke="{color}" stroke-width="{sw*0.5:.1f}"/>')
    x, y = P((0,0)); out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{sw*1.1:.1f}" fill="{joint}" stroke="{color}" stroke-width="{sw*0.5:.1f}"/>')
    return "\n".join(out)

def fist(cx, cy, color, fill):
    g = [f'<g transform="translate({cx} {cy}) scale(0.8)" fill="{fill}" stroke="{color}" stroke-width="4" stroke-linejoin="round" stroke-linecap="round">']
    g.append('<path d="M-46 -78 L-46 -40 C-46 -14 -34 0 -22 6 L-22 26 L26 26 L26 6 C40 -2 48 -18 48 -40 L48 -78 Z"/>')
    for i in range(4):
        g.append(f'<rect x="{-46 + i*23.5}" y="-104" width="23.5" height="42" rx="11.75"/>')
    g.append('<path d="M-50 -58 C-50 -70 -40 -72 -30 -68 L4 -54 C14 -50 14 -36 4 -34 L-24 -32 C-40 -32 -50 -42 -50 -58 Z"/>')
    g.append('</g>')
    return "".join(g)

def arrow(x1, y1, x2, y2, color, sw=3):
    import math
    a = math.atan2(y2-y1, x2-x1); L = 9
    p1 = (x2 - L*math.cos(a-0.5), y2 - L*math.sin(a-0.5)); p2 = (x2 - L*math.cos(a+0.5), y2 - L*math.sin(a+0.5))
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="{sw}" stroke-linecap="round"/>'
            f'<polyline points="{p1[0]:.1f},{p1[1]:.1f} {x2},{y2} {p2[0]:.1f},{p2[1]:.1f}" fill="none" stroke="{color}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round"/>')

def defs(t):
    return f'''<defs>
  <filter id="sh" x="-20%" y="-20%" width="140%" height="160%"><feDropShadow dx="0" dy="8" stdDeviation="14" flood-color="#000" flood-opacity="{t["shadow"]}"/></filter>
  <filter id="shs" x="-20%" y="-20%" width="140%" height="160%"><feDropShadow dx="0" dy="2" stdDeviation="4" flood-color="#000" flood-opacity="{float(t["shadow"])*0.6:.2f}"/></filter>
  <linearGradient id="acc" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="{t["teal"]}"/><stop offset="1" stop-color="{t["blue"]}"/></linearGradient>
  <radialGradient id="glow" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="{t["teal"]}" stop-opacity="0.22"/><stop offset="1" stop-color="{t["teal"]}" stop-opacity="0"/></radialGradient>
</defs>'''

def svg(w, h, t, body):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" font-family="{FONT}">\n{defs(t)}\n<rect width="{w}" height="{h}" rx="28" fill="{t["bg"]}"/>\n{body}\n</svg>\n'

def phone(x, y, t, inner):
    return f'''<g transform="translate({x} {y})" filter="url(#sh)">
  <rect width="200" height="400" rx="40" fill="{t["device"]}"/>
  <rect x="9" y="9" width="182" height="382" rx="32" fill="{t["screen"]}"/>
  <rect x="72" y="22" width="56" height="16" rx="8" fill="{t["device"]}"/>
</g>
<g transform="translate({x} {y})">{inner}</g>'''

def monitor(x, y, t, inner):
    return f'''<g transform="translate({x} {y})" filter="url(#sh)">
  <rect width="340" height="220" rx="18" fill="{t["device"]}"/>
  <rect x="8" y="8" width="324" height="204" rx="11" fill="{t["screen"]}"/>
  <path d="M140 220 L130 262 L210 262 L200 220 Z" fill="{t["device"]}"/>
  <rect x="104" y="260" width="132" height="8" rx="4" fill="{t["device"]}"/>
</g>
<g transform="translate({x} {y})">{inner}</g>'''

def cursor(x, y, t):
    return f'<path transform="translate({x} {y})" d="M0 0 L0 30 L8 22 L14 36 L20 33 L14 20 L25 20 Z" fill="{t["text"]}" stroke="{t["surface"]}" stroke-width="2" stroke-linejoin="round" filter="url(#shs)"/>'

def banner(t):
    W, H = 1280, 740
    body = f'''
<text x="640" y="104" text-anchor="middle" font-size="22" font-weight="600" letter-spacing="0.4" fill="{t["teal"]}">GestureLink</text>
<text x="640" y="184" text-anchor="middle" font-size="76" font-weight="700" letter-spacing="-2.4" fill="{t["text"]}">Your hand is the mouse.</text>
<text x="640" y="232" text-anchor="middle" font-size="25" font-weight="400" letter-spacing="-0.2" fill="{t["sec"]}">Control a Windows PC with gestures, seen by your Android phone's camera.</text>
<ellipse cx="400" cy="490" rx="200" ry="150" fill="url(#glow)"/>
{phone(300, 290, t, hand(100, 300, 1.25, OPEN, t["teal"], t["joint"], sw=4) + f'<circle cx="100" cy="225" r="16" fill="none" stroke="{t["amber"]}" stroke-width="3"/>')}
<g>
  <path d="M540 490 L 700 490" stroke="url(#acc)" stroke-width="4" stroke-linecap="round" stroke-dasharray="0.1 14"/>
  <g filter="url(#shs)"><rect x="570" y="466" width="100" height="48" rx="24" fill="{t["surface"]}"/></g>
  <path d="M598 486 v-5 a8 8 0 0 1 16 0 v5" fill="none" stroke="{t["text"]}" stroke-width="2.4" stroke-linecap="round"/>
  <rect x="594" y="486" width="24" height="17" rx="4" fill="{t["text"]}"/>
  <text x="628" y="499" font-size="15" font-weight="600" letter-spacing="0.3" fill="{t["text"]}">TLS</text>
</g>
{monitor(720, 356, t, f'<rect x="32" y="34" width="96" height="10" rx="5" fill="{t["line"]}"/><rect x="32" y="54" width="170" height="10" rx="5" fill="{t["line"]}"/><rect x="32" y="74" width="132" height="10" rx="5" fill="{t["line"]}"/><path d="M60 170 C 110 110, 170 190, 228 128" fill="none" stroke="{t["teal"]}" stroke-width="4" stroke-linecap="round" stroke-dasharray="0.1 10"/>' + cursor(228, 126, t))}
'''
    return svg(W, H, t, body)

def gestures(t):
    W, H = 1280, 420
    cards = [
      ("Move", "Open hand moves the cursor.", lambda cx, cy: hand(cx, cy, 0.95, OPEN, t["teal"], t["joint"], sw=3.2)
          + arrow(cx-70, cy-60, cx-100, cy-60, t["sec"], 2.5) + arrow(cx+70, cy-60, cx+100, cy-60, t["sec"], 2.5)),
      ("Click &amp; drag", "A fist holds the button down.", lambda cx, cy: fist(cx, cy-20, t["amber"], t["surface"])
          + f'<circle cx="{cx+1}" cy="{cy-60}" r="52" fill="none" stroke="{t["amber"]}" stroke-opacity="0.35" stroke-width="2.5"/>'
          + f'<circle cx="{cx+1}" cy="{cy-60}" r="68" fill="none" stroke="{t["amber"]}" stroke-opacity="0.15" stroke-width="2.5"/>'),
      ("Zoom", "Move two hands apart or together.", lambda cx, cy: hand(cx-52, cy, 0.7, OPEN, t["blue"], t["joint"], mirror=True, sw=2.8)
          + hand(cx+52, cy, 0.7, OPEN, t["teal"], t["joint"], sw=2.8)
          + arrow(cx-80, cy+22, cx-112, cy+22, t["sec"], 2.5) + arrow(cx+80, cy+22, cx+112, cy+22, t["sec"], 2.5)),
      ("Scroll", "Left open hand, right hand in view.", lambda cx, cy: hand(cx, cy, 0.95, OPEN, t["blue"], t["joint"], mirror=True, sw=3.2)
          + arrow(cx+82, cy-50, cx+82, cy-96, t["sec"], 2.5) + arrow(cx+82, cy-30, cx+82, cy+16, t["sec"], 2.5)),
    ]
    parts = []
    cw, gap, x0, y0, ch = 284, 24, 40, 40, 340
    for i, (title, cap, draw) in enumerate(cards):
        x = x0 + i*(cw+gap)
        parts.append(f'<g filter="url(#shs)"><rect x="{x}" y="{y0}" width="{cw}" height="{ch}" rx="22" fill="{t["surface"]}"/></g>')
        parts.append(draw(x + cw/2, y0 + 200))
        parts.append(f'<text x="{x+cw/2}" y="{y0+262}" text-anchor="middle" font-size="24" font-weight="600" letter-spacing="-0.4" fill="{t["text"]}">{title}</text>')
        parts.append(f'<text x="{x+cw/2}" y="{y0+292}" text-anchor="middle" font-size="16" letter-spacing="0" fill="{t["sec"]}">{cap}</text>')
    return svg(W, H, t, "\n".join(parts))

def how(t):
    W, H = 1280, 360
    steps = [
      ("Sees", "The camera finds your hands.", "Frames never leave the phone."),
      ("Understands", "21 points tracked per hand.", "Gestures become intent."),
      ("Sends", "Only small control messages.", "Encrypted, pinned TLS."),
      ("Moves", "Your real cursor moves.", "Released the instant you stop."),
    ]
    cols = [t["teal"], t["teal"], t["blue"], t["blue"]]
    parts = []
    cw, gap, x0, y0, ch = 284, 24, 40, 40, 280
    for i, (title, l1, l2) in enumerate(steps):
        x = x0 + i*(cw+gap)
        parts.append(f'<g filter="url(#shs)"><rect x="{x}" y="{y0}" width="{cw}" height="{ch}" rx="22" fill="{t["surface"]}"/></g>')
        parts.append(f'<circle cx="{x+44}" cy="{y0+56}" r="20" fill="{cols[i]}" fill-opacity="0.14"/>')
        parts.append(f'<text x="{x+44}" y="{y0+63}" text-anchor="middle" font-size="19" font-weight="700" fill="{cols[i]}">{i+1}</text>')
        parts.append(f'<text x="{x+28}" y="{y0+136}" font-size="34" font-weight="700" letter-spacing="-1" fill="{t["text"]}">{title}</text>')
        parts.append(f'<text x="{x+28}" y="{y0+180}" font-size="16.5" fill="{t["text"]}">{l1}</text>')
        parts.append(f'<text x="{x+28}" y="{y0+206}" font-size="16.5" fill="{t["sec"]}">{l2}</text>')
        dev = "Android" if i < 2 else ("Network" if i == 2 else "Windows")
        parts.append(f'<text x="{x+28}" y="{y0+250}" font-size="13" font-weight="600" letter-spacing="1.2" fill="{t["sec"]}">{dev.upper()}</text>')
    return svg(W, H, t, "\n".join(parts))

for name, fn in (("banner", banner), ("gestures", gestures), ("how-it-works", how)):
    for theme, t in THEMES.items():
        with open(os.path.join(OUT, f"{name}-{theme}.svg"), "w") as f: f.write(fn(t))
