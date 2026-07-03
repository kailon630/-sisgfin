#!/bin/bash
# Inicia XWayland se não estiver rodando
if ! ls /tmp/.X11-unix/X1 2>/dev/null; then
    Xwayland :1 &
    sleep 1
fi
DISPLAY=:1 ./build/compose/binaries/main/app/SisgFin/bin/SisgFin
