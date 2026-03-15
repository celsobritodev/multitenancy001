#!/usr/bin/env python3
import argparse, json, os, re
from collections import Counter

FINAL_RE = re.compile(r"FINAL_RESULT\s+workerIndex=(?P<worker_index>\d+)\s+attempt=(?P<attempt>\d+)\s+code=(?P<code>\d+)\s+apiCode=(?P<api>.*?)\s+status=(?P<status>\S+)\s+affectsInventory=(?P<affects>[01])\s+elapsedMs=(?P<elapsed>\d+)\s+saleId=(?P<saleid>[^\s]*)")
LEGACY_RE = re.compile(r"attempt=(?P<attempt>\d+)\s+(?:workerIndex=(?P<worker_index>\d+)\s+)?code=(?P<code>\d+)\s+apiCode=(?P<api>.*?)\s+status=(?P<status>\S+)\s+affectsInventory=(?P<affects>[01])\s+elapsedMs=(?P<elapsed>\d+)\s+saleId=(?P<saleid>[^\s]*)")

def int_or_zero(v):
    try:
        return int(float(str(v).strip() or '0'))
    except Exception:
        return 0

def normalize_row(row, source):
    return {
        'worker_index': str(row.get('worker_index', '') or ''),
        'attempt': int_or_zero(row.get('attempt', 0)),
        'code': int_or_zero(row.get('code', 0)),
        'api_code': str(row.get('api_code', '') or ''),
        'status': str(row.get('status', '') or ''),
        'affects': 1 if str(row.get('affects', '0')) == '1' else 0,
        'elapsed_ms': int_or_zero(row.get('elapsed_ms', 0)),
        'sale_id': str(row.get('sale_id', '') or ''),
        'source': source,
    }

def parse_statuses(path):
    rows=[]
    if not os.path.exists(path):
        return rows
    with open(path,'r',encoding='utf-8',errors='ignore') as f:
        for raw in f:
            line=raw.rstrip('\n\r')
            if not line.strip():
                continue
            parts=line.split('\t') + [''] * 6
            rows.append(normalize_row({
                'code': parts[0].strip(),
                'api_code': parts[1].strip(),
                'status': parts[2].strip(),
                'affects': parts[3].strip(),
                'sale_id': parts[4].strip(),
                'elapsed_ms': parts[5].strip(),
            }, 'statuses'))
    return rows

def parse_worker_file(path, worker_index):
    final=None
    legacy=None
    with open(path,'r',encoding='utf-8',errors='ignore') as f:
        for line in f:
            m = FINAL_RE.search(line)
            if m:
                gd = m.groupdict()
                final = normalize_row({
                    'worker_index': gd.get('worker_index') or worker_index,
                    'attempt': gd.get('attempt'),
                    'code': gd.get('code'),
                    'api_code': gd.get('api'),
                    'status': gd.get('status'),
                    'affects': gd.get('affects'),
                    'elapsed_ms': gd.get('elapsed'),
                    'sale_id': gd.get('saleid'),
                }, 'worker_final')
                continue
            m = LEGACY_RE.search(line)
            if m:
                gd = m.groupdict()
                legacy = normalize_row({
                    'worker_index': gd.get('worker_index') or worker_index,
                    'attempt': gd.get('attempt'),
                    'code': gd.get('code'),
                    'api_code': gd.get('api'),
                    'status': gd.get('status'),
                    'affects': gd.get('affects'),
                    'elapsed_ms': gd.get('elapsed'),
                    'sale_id': gd.get('saleid'),
                }, 'worker_legacy')
    return final or legacy

def parse_worker_tsv(path, worker_index):
    if not os.path.exists(path):
        return None
    with open(path,'r',encoding='utf-8',errors='ignore') as f:
        line = f.readline().rstrip('\n\r')
    if not line:
        return None
    parts = line.split('\t') + [''] * 6
    return normalize_row({
        'worker_index': worker_index,
        'code': parts[0].strip(),
        'api_code': parts[1].strip(),
        'status': parts[2].strip(),
        'affects': parts[3].strip(),
        'sale_id': parts[4].strip(),
        'elapsed_ms': parts[5].strip(),
    }, 'worker_tsv')

def parse_workers(worker_dir):
    rows=[]
    warnings=[]
    if not os.path.isdir(worker_dir):
        return rows, warnings
    by_worker={}
    names=sorted(os.listdir(worker_dir))
    for name in names:
        if name.startswith('worker_') and name.endswith('.log'):
            worker_index=name[len('worker_'):-len('.log')]
            row=parse_worker_file(os.path.join(worker_dir,name), worker_index)
            if row:
                by_worker[worker_index]=row
    for name in names:
        if name.startswith('worker_') and name.endswith('.final.tsv'):
            worker_index=name[len('worker_'):-len('.final.tsv')]
            if worker_index in by_worker:
                continue
            row=parse_worker_tsv(os.path.join(worker_dir,name), worker_index)
            if row:
                by_worker[worker_index]=row
    if not by_worker:
        warnings.append('worker_logs_empty')
    rows=[by_worker[k] for k in sorted(by_worker, key=int_or_zero)]
    return rows, warnings

def summarize(final_rows, status_rows_count, worker_rows_count, warnings):
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
        if sid and sid not in sample:
            sample.append(sid)
        if len(sample)>=10:
            break
    return {
        'parser_version':'v25.2',
        'source_of_truth':'worker_logs' if worker_rows_count else 'statuses_tsv',
        'warnings': warnings,
        'status_rows_count': status_rows_count,
        'worker_rows_count': worker_rows_count,
        'final_rows_count': len(final_rows),
        'success_count': len(success),
        'failure_count': len(failure),
        'accepted_affecting_count': len(affecting),
        'accepted_non_affecting_count': len(non_affecting),
        'rollback_like_count': rollback_like,
        'count_200_201': http_counts.get('200', 0) + http_counts.get('201', 0),
        'count_400': http_counts.get('400', 0),
        'count_401': http_counts.get('401', 0),
        'count_403': http_counts.get('403', 0),
        'count_404': http_counts.get('404', 0),
        'count_409_total': http_counts.get('409', 0),
        'count_500': http_counts.get('500', 0),
        'count_409_insufficient': api_counts.get('INSUFFICIENT_STOCK', 0),
        'http_counts': dict(http_counts),
        'api_counts': dict(api_counts),
        'http_api_counts': dict(http_api_counts),
        'sample_sale_ids': sample,
        'rows': final_rows,
    }

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--statuses', required=True)
    ap.add_argument('--worker-dir', required=True)
    ap.add_argument('--output-json', required=True)
    ap.add_argument('--elapsed-out', required=True)
    args=ap.parse_args()
    status_rows=parse_statuses(args.statuses)
    worker_rows,warnings=parse_workers(args.worker_dir)
    final_rows=worker_rows if worker_rows else status_rows
    payload=summarize(final_rows, len(status_rows), len(worker_rows), warnings)
    with open(args.elapsed_out,'w',encoding='utf-8') as ef:
        for r in final_rows:
            ef.write(str(int_or_zero(r.get('elapsed_ms', 0))) + '\n')
    with open(args.output_json,'w',encoding='utf-8') as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

if __name__ == '__main__':
    main()
