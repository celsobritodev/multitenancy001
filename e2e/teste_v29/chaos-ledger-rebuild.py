#!/usr/bin/env python3
import sys, json

def rebuild(movements):
    qty = 0
    for m in movements:
        t = str(m.get("type","")).upper()
        q = int(m.get("quantity",0))
        if t in ("IN","INBOUND","CREDIT"):
            qty += q
        elif t in ("OUT","OUTBOUND","DEBIT"):
            qty -= q
    return qty

if __name__ == "__main__":
    data = json.load(sys.stdin)
    movements = data if isinstance(data,list) else data.get("content",[])
    print(rebuild(movements))
