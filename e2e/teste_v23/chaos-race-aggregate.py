#!/usr/bin/env python3
import argparse, json, os, re
from collections import Counter
FINAL_RE = re.compile(r"attempt=(?P<attempt>\d+)\s+code=(?P<code>\d+)\s+apiCode=(?P<api>.*?)\s+status=(?P<status>\S+)\s+affectsInventory=(?P<affects>[01])\s+elapsedMs=(?P<elapsed>\d+)\s+saleId=(?P<saleid>[^\s]*)")

def parse_statuses(path):
    rows=[]
    if not os.path.exists(path): return rows
    with open(path,'r',encoding='utf-8',errors='ignore') as f:
        for raw in f:
            line=raw.rstrip('\n\r')
            if not line.strip(): continue
            parts=line.split('\t')+['']*6
            try: code=int(parts[0].strip() or '0')
            except Exception: continue
            try: elapsed=int(float(parts[5].strip() or '0'))
            except Exception: elapsed=0
            rows.append({'code':code,'api_code':parts[1].strip(),'status':parts[2].strip(),'affects':1 if parts[3].strip()=='1' else 0,'sale_id':parts[4].strip(),'elapsed_ms':elapsed,'source':'statuses'})
    return rows

def parse_workers(worker_dir):
    rows=[]
    if not os.path.isdir(worker_dir): return rows
    for name in sorted(os.listdir(worker_dir)):
        if not (name.startswith('worker_') and name.endswith('.log')): continue
        path=os.path.join(worker_dir,name)
        last=None
        with open(path,'r',encoding='utf-8',errors='ignore') as f:
            for line in f:
                m=FINAL_RE.search(line)
                if m: last=m
        if not last: continue
        gd=last.groupdict()
        rows.append({'worker_index':name[7:-4],'attempt':int(gd['attempt']),'code':int(gd['code']),'api_code':gd['api'],'status':gd['status'],'affects':1 if gd['affects']=='1' else 0,'elapsed_ms':int(gd['elapsed']),'sale_id':gd['saleid'],'source':'worker'})
    return rows

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--statuses',required=True)
    ap.add_argument('--worker-dir',required=True)
    ap.add_argument('--output-json',required=True)
    ap.add_argument('--elapsed-out',required=True)
    args=ap.parse_args()
    status_rows=parse_statuses(args.statuses)
    worker_rows=parse_workers(args.worker_dir)
    final_rows=worker_rows if worker_rows else status_rows
    success=[r for r in final_rows if r['code'] in (200,201)]
    failure=[r for r in final_rows if r['code'] not in (200,201)]
    affecting=[r for r in success if r['affects']==1]
    non_affecting=[r for r in success if r['affects']!=1]
    http_counts=Counter(str(r['code']) for r in final_rows)
    api_counts=Counter((r.get('api_code') or '') for r in final_rows if (r.get('api_code') or ''))
    http_api_counts=Counter(f"{r['code']}|{r.get('api_code') or ''}" for r in final_rows)
    rollback_like=sum(1 for r in final_rows if r['code'] in (400,404,409,422))
    sample=[]
    for r in success:
        sid=r.get('sale_id') or ''
        if sid and sid not in sample: sample.append(sid)
        if len(sample)>=10: break
    with open(args.elapsed_out,'w',encoding='utf-8') as ef:
        for r in final_rows: ef.write(str(int(r.get('elapsed_ms') or 0))+'\n')
    payload={'source_of_truth':'worker_logs' if worker_rows else 'statuses_tsv','status_rows_count':len(status_rows),'worker_rows_count':len(worker_rows),'final_rows_count':len(final_rows),'success_count':len(success),'failure_count':len(failure),'accepted_affecting_count':len(affecting),'accepted_non_affecting_count':len(non_affecting),'rollback_like_count':rollback_like,'count_200_201':http_counts.get('200',0)+http_counts.get('201',0),'count_400':http_counts.get('400',0),'count_401':http_counts.get('401',0),'count_403':http_counts.get('403',0),'count_404':http_counts.get('404',0),'count_409_total':http_counts.get('409',0),'count_500':http_counts.get('500',0),'count_409_insufficient':api_counts.get('INSUFFICIENT_STOCK',0),'http_counts':dict(http_counts),'api_counts':dict(api_counts),'http_api_counts':dict(http_api_counts),'sample_sale_ids':sample}
    with open(args.output_json,'w',encoding='utf-8') as f: json.dump(payload,f,ensure_ascii=False,indent=2)
if __name__=='__main__': main()
