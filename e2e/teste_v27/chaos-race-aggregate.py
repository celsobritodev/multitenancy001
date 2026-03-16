#!/usr/bin/env python3
import argparse
import json
import os
from collections import Counter
from statistics import mean


def int_or_zero(v):
    try:
        return int(float(str(v).strip() or '0'))
    except Exception:
        return 0


def str_or_empty(v):
    return '' if v is None else str(v)


def parse_kv_line(line):
    data = {}
    for token in line.strip().split():
        if '=' not in token:
            continue
        key, value = token.split('=', 1)
        data[key] = value
    return data


def normalize_row(row, source):
    return {
        'worker_index': str_or_empty(row.get('worker_index', row.get('workerIndex', ''))),
        'attempt': int_or_zero(row.get('attempt', 0)),
        'retry_used': int_or_zero(row.get('retry_used', row.get('retryUsed', 0))),
        'code': int_or_zero(row.get('code', 0)),
        'api_code': str_or_empty(row.get('api_code') or row.get('apiCode')),
        'status': str_or_empty(row.get('status', '')),
        'affects': 1 if str(row.get('affects', row.get('affectsInventory', '0'))) == '1' else 0,
        'elapsed_ms': int_or_zero(row.get('elapsed_ms', row.get('elapsedMs', 0))),
        'monotonic_elapsed_ms': int_or_zero(row.get('monotonic_elapsed_ms', row.get('monotonicElapsedMs', 0))),
        'worker_total_monotonic_ms': int_or_zero(row.get('worker_total_monotonic_ms', row.get('workerTotalMonotonicMs', 0))),
        'monotonic_start_ns': int_or_zero(row.get('monotonic_start_ns', row.get('monotonicStartNs', 0))),
        'monotonic_end_ns': int_or_zero(row.get('monotonic_end_ns', row.get('monotonicEndNs', 0))),
        'worker_start_monotonic_ns': int_or_zero(row.get('worker_start_monotonic_ns', row.get('workerStartMonotonicNs', 0))),
        'worker_end_monotonic_ns': int_or_zero(row.get('worker_end_monotonic_ns', row.get('workerEndMonotonicNs', 0))),
        'epoch_start_ms': int_or_zero(row.get('epoch_start_ms', row.get('epochStartMs', 0))),
        'epoch_end_ms': int_or_zero(row.get('epoch_end_ms', row.get('epochEndMs', 0))),
        'sale_id': str_or_empty(row.get('sale_id', row.get('saleId', ''))),
        'correlation_id': str_or_empty(row.get('correlation_id') or row.get('correlationId')),
        'retry_trace': str_or_empty(row.get('retry_trace') or row.get('retryTrace')),
        'retry_api_trace': str_or_empty(row.get('retry_api_trace') or row.get('retryApiTrace')),
        'latency_trace_ms': str_or_empty(row.get('latency_trace_ms') or row.get('latencyTraceMs')),
        'correlation_trace': str_or_empty(row.get('correlation_trace') or row.get('correlationTrace')),
        'source': source,
    }


def parse_statuses(path):
    rows = []
    if not os.path.exists(path):
        return rows
    with open(path, 'r', encoding='utf-8', errors='ignore') as f:
        for raw in f:
            line = raw.rstrip('\n\r')
            if not line.strip():
                continue
            parts = line.split('\t') + [''] * 13
            rows.append(normalize_row({
                'code': parts[0].strip(),
                'api_code': parts[1].strip(),
                'status': parts[2].strip(),
                'affects': parts[3].strip(),
                'sale_id': parts[4].strip(),
                'elapsed_ms': parts[5].strip(),
                'correlation_id': parts[6].strip(),
                'retry_used': parts[7].strip(),
                'monotonic_elapsed_ms': parts[8].strip(),
                'worker_total_monotonic_ms': parts[9].strip(),
                'retry_trace': parts[10].strip(),
                'latency_trace_ms': parts[11].strip(),
                'correlation_trace': parts[12].strip(),
            }, 'statuses'))
    return rows


def parse_attempt_rows(path, worker_index):
    rows = []
    if not os.path.exists(path):
        return rows
    with open(path, 'r', encoding='utf-8', errors='ignore') as f:
        for line in f:
            if 'ATTEMPT_RESULT ' not in line:
                continue
            kv = parse_kv_line(line)
            kv.setdefault('worker_index', worker_index)
            rows.append(normalize_row(kv, 'worker_attempt'))
    return rows


def parse_final_row(path, worker_index):
    final = None
    if not os.path.exists(path):
        return None
    with open(path, 'r', encoding='utf-8', errors='ignore') as f:
        for line in f:
            if 'FINAL_RESULT ' not in line:
                continue
            kv = parse_kv_line(line)
            kv.setdefault('worker_index', worker_index)
            final = normalize_row(kv, 'worker_final')
    return final


def parse_worker_tsv(path, worker_index):
    if not os.path.exists(path):
        return None
    with open(path, 'r', encoding='utf-8', errors='ignore') as f:
        line = f.readline().rstrip('\n\r')
    if not line:
        return None
    parts = line.split('\t') + [''] * 13
    return normalize_row({
        'worker_index': worker_index,
        'code': parts[0].strip(),
        'api_code': parts[1].strip(),
        'status': parts[2].strip(),
        'affects': parts[3].strip(),
        'sale_id': parts[4].strip(),
        'elapsed_ms': parts[5].strip(),
        'correlation_id': parts[6].strip(),
        'retry_used': parts[7].strip(),
        'monotonic_elapsed_ms': parts[8].strip(),
        'worker_total_monotonic_ms': parts[9].strip(),
        'retry_trace': parts[10].strip(),
        'latency_trace_ms': parts[11].strip(),
        'correlation_trace': parts[12].strip(),
    }, 'worker_tsv')


def parse_workers(worker_dir):
    final_rows = []
    warnings = []
    attempt_rows = []
    if not os.path.isdir(worker_dir):
        return final_rows, attempt_rows, warnings
    by_worker = {}
    names = sorted(os.listdir(worker_dir))
    for name in names:
        if name.startswith('worker_') and name.endswith('.log'):
            worker_index = name[len('worker_'):-len('.log')]
            log_path = os.path.join(worker_dir, name)
            attempt_rows.extend(parse_attempt_rows(log_path, worker_index))
            row = parse_final_row(log_path, worker_index)
            if row:
                by_worker[worker_index] = row
    for name in names:
        if name.startswith('worker_') and name.endswith('.final.tsv'):
            worker_index = name[len('worker_'):-len('.final.tsv')]
            if worker_index in by_worker:
                continue
            row = parse_worker_tsv(os.path.join(worker_dir, name), worker_index)
            if row:
                by_worker[worker_index] = row
    if not by_worker:
        warnings.append('worker_logs_empty')
    final_rows = [by_worker[k] for k in sorted(by_worker, key=int_or_zero)]
    if final_rows and len(attempt_rows) < len(final_rows):
        warnings.append('attempt_rows_lt_final_rows')
    return final_rows, attempt_rows, warnings


def percentile(sorted_values, p):
    if not sorted_values:
        return 0
    idx = max(0, min(len(sorted_values) - 1, int(round((p / 100.0) * len(sorted_values) + 0.499999)) - 1))
    return sorted_values[idx]


def metrics(values):
    vals = [int_or_zero(v) for v in values if int_or_zero(v) >= 0]
    if not vals:
        return {'count': 0, 'avg': 0, 'p50': 0, 'p95': 0, 'max': 0, 'min': 0}
    ordered = sorted(vals)
    return {
        'count': len(ordered),
        'avg': int(round(mean(ordered))),
        'p50': percentile(ordered, 50),
        'p95': percentile(ordered, 95),
        'max': ordered[-1],
        'min': ordered[0],
    }


def summarize(final_rows, attempt_rows, status_rows_count, worker_rows_count, warnings):
    success = [r for r in final_rows if r['code'] in (200, 201)]
    failure = [r for r in final_rows if r['code'] not in (200, 201)]
    affecting = [r for r in success if r['affects'] == 1]
    non_affecting = [r for r in success if r['affects'] != 1]
    http_counts = Counter(str(r['code']) for r in final_rows)
    api_counts = Counter((r.get('api_code') or '') for r in final_rows if (r.get('api_code') or ''))
    http_api_counts = Counter(f"{r['code']}|{r.get('api_code') or ''}" for r in final_rows)
    rollback_like = sum(1 for r in final_rows if r['code'] in (400, 404, 409, 422))
    sample = []
    sample_correlation_ids = []
    for r in success:
        sid = r.get('sale_id') or ''
        cid = r.get('correlation_id') or ''
        if sid and sid not in sample:
            sample.append(sid)
        if cid and cid not in sample_correlation_ids:
            sample_correlation_ids.append(cid)
        if len(sample) >= 10 and len(sample_correlation_ids) >= 10:
            break
    final_latency_metrics = metrics([r.get('elapsed_ms', 0) for r in final_rows])
    monotonic_latency_metrics = metrics([r.get('monotonic_elapsed_ms', 0) for r in final_rows])
    worker_total_metrics = metrics([r.get('worker_total_monotonic_ms', 0) for r in final_rows])
    attempt_latency_metrics = metrics([r.get('monotonic_elapsed_ms', 0) for r in attempt_rows])
    retry_used_metrics = metrics([r.get('retry_used', 0) for r in final_rows])
    monotonic_valid = all((r.get('monotonic_end_ns', 0) >= r.get('monotonic_start_ns', 0) >= 0) for r in final_rows)
    correlation_complete = all(bool(r.get('correlation_id')) for r in final_rows)
    correlation_unique = len({r.get('correlation_id') for r in final_rows if r.get('correlation_id')}) == len(final_rows)
    return {
        'parser_version': 'v27',
        'source_of_truth': 'worker_logs' if worker_rows_count else 'statuses_tsv',
        'warnings': warnings,
        'status_rows_count': status_rows_count,
        'worker_rows_count': worker_rows_count,
        'attempt_rows_count': len(attempt_rows),
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
        'sample_correlation_ids': sample_correlation_ids,
        'final_latency_metrics_ms': final_latency_metrics,
        'monotonic_latency_metrics_ms': monotonic_latency_metrics,
        'worker_total_metrics_ms': worker_total_metrics,
        'attempt_latency_metrics_ms': attempt_latency_metrics,
        'retry_used_metrics': retry_used_metrics,
        'monotonic_timestamps_valid': monotonic_valid,
        'correlation_ids_complete': correlation_complete,
        'correlation_ids_unique': correlation_unique,
        'rows': final_rows,
        'attempt_rows': attempt_rows,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--statuses', required=True)
    ap.add_argument('--worker-dir', required=True)
    ap.add_argument('--output-json', required=True)
    ap.add_argument('--elapsed-out', required=True)
    args = ap.parse_args()
    status_rows = parse_statuses(args.statuses)
    worker_rows, attempt_rows, warnings = parse_workers(args.worker_dir)
    final_rows = worker_rows if worker_rows else status_rows
    payload = summarize(final_rows, attempt_rows, len(status_rows), len(worker_rows), warnings)
    with open(args.elapsed_out, 'w', encoding='utf-8') as ef:
        for r in final_rows:
            ef.write(str(int_or_zero(r.get('elapsed_ms', 0))) + '\n')
    with open(args.output_json, 'w', encoding='utf-8') as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

if __name__ == '__main__':
    main()
