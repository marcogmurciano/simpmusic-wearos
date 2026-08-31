#!/usr/bin/env python3
"""Genera la pista de prueba de F1 (tono de 440 Hz, 8 s) en res/raw/test_tone.wav."""
import math, struct, sys, wave

out = sys.argv[1] if len(sys.argv) > 1 else "wearApp/src/main/res/raw/test_tone.wav"
with wave.open(out, "w") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(22050)
    frames = bytearray()
    for i in range(22050 * 8):
        t = i / 22050
        amp = 12000 * min(1.0, t * 2) * min(1.0, (8 - t) * 2)   # envolvente para evitar clics
        frames += struct.pack("<h", int(amp * math.sin(2 * math.pi * 440 * t)))
    w.writeframes(bytes(frames))
print(f"generado {out}")
