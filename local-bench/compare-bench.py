#!/usr/bin/env python3
"""
Compare two mini-testing runs from their TESTRUN log streams.

Both branches emit identical `TESTRUN PROGRESS`/`TESTRUN END` lines, so the
comparison is deterministic and reproducible from the logs alone.

Headline metric: wall-clock time to process the first N tests, where N is the
largest test count BOTH runs reached (equal-work comparison, robust to the fact
that the slow branch may never finish the full matrix).

Usage:
  local-bench/compare-bench.py run-fast-*.log run-slow-*.log [--n N] [--label-a fast --label-b slow]
"""
import argparse, re, sys

PROG = re.compile(r'TESTRUN PROGRESS\b')
END  = re.compile(r'TESTRUN END\b')

def num(pattern, line, cast=int, default=None):
    m = re.search(pattern, line)
    return cast(m.group(1)) if m else default

def parse(path):
    samples = []   # (elapsed_s, done, timeout, avg_ms, max_ms, stuck)
    end = {}
    with open(path, errors='replace') as f:
        for line in f:
            if PROG.search(line):
                elapsed = num(r'elapsed=(\d+)s', line)
                done    = num(r'done=(\d+)/', line)
                if elapsed is None or done is None:
                    continue
                samples.append({
                    'elapsed': elapsed,
                    'done':    done,
                    'timeout': num(r'\btimeout=(\d+)', line, default=0),
                    'avg':     num(r'avgTestMs=(-?\d+)', line, default=-1),
                    'max':     num(r'maxTestMs=(\d+)', line, default=0),
                    'stuck':   num(r'stuckSlots=(\d+)', line, default=0),
                })
            elif END.search(line):
                end = {
                    'reason':   (re.search(r'reason=(\S+)', line) or [None, '?'])[1],
                    'duration': num(r'durationSec=(\d+)', line, default=0),
                    'done':     num(r'done=(\d+)/', line, default=0),
                    'timeout':  num(r'\btimeout=(\d+)', line, default=0),
                    'accounted':num(r'accountedFor=(\d+)', line, default=0),
                }
    return samples, end

def time_to_n(samples, n):
    """Linear-interpolate elapsed seconds at which done crosses n."""
    prev = None
    for s in samples:
        if s['done'] >= n:
            if prev is None or s['done'] == prev['done']:
                return s['elapsed']
            frac = (n - prev['done']) / (s['done'] - prev['done'])
            return prev['elapsed'] + frac * (s['elapsed'] - prev['elapsed'])
        prev = s
    return None

def steady_throughput(samples):
    """tests/sec over the span from first non-zero progress to last sample."""
    nz = [s for s in samples if s['done'] > 0]
    if len(nz) < 2:
        return None
    a, b = nz[0], nz[-1]
    dt = b['elapsed'] - a['elapsed']
    return (b['done'] - a['done']) / dt if dt > 0 else None

def final_avg(samples):
    nz = [s for s in samples if s['avg'] >= 0]
    return nz[-1]['avg'] if nz else None

def summarize(path):
    s, e = parse(path)
    return {
        'path': path, 'samples': s, 'end': e,
        'final_done': (s[-1]['done'] if s else e.get('done', 0)),
        'peak_stuck': max((x['stuck'] for x in s), default=0),
        'final_avg': final_avg(s),
        'final_max': max((x['max'] for x in s), default=0),
        'final_timeout': (s[-1]['timeout'] if s else e.get('timeout', 0)),
        'steady': steady_throughput(s),
        'duration': e.get('duration'),
        'reason': e.get('reason', '(no END)'),
    }

def fmt(v, unit='', nd=1):
    if v is None: return 'n/a'
    return f'{v:.{nd}f}{unit}' if isinstance(v, float) else f'{v}{unit}'

def ratio(a, b):
    if not a or not b: return 'n/a'
    return f'{a / b:.2f}x' if b else 'n/a'

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('log_a'); ap.add_argument('log_b')
    ap.add_argument('--n', type=int, default=None, help='equal-work test count (default: min of both finals)')
    ap.add_argument('--label-a', default='A'); ap.add_argument('--label-b', default='B')
    args = ap.parse_args()

    A = summarize(args.log_a); B = summarize(args.log_b)
    la, lb = args.label_a, args.label_b

    n = args.n or (min(A['final_done'], B['final_done']) // 5000) * 5000
    if n < 1000:
        n = min(A['final_done'], B['final_done'])
    ttn_a, ttn_b = time_to_n(A['samples'], n), time_to_n(B['samples'], n)
    thr_a = (n / ttn_a) if ttn_a else None      # tests/s including cold start, over equal work
    thr_b = (n / ttn_b) if ttn_b else None

    rows = [
        ('run reason',            A['reason'],               B['reason'],            ''),
        ('final done',            A['final_done'],           B['final_done'],        ''),
        (f'time to {n} tests (s)', fmt(ttn_a), fmt(ttn_b),   ratio(ttn_b, ttn_a) + ' (A faster if >1)'),
        (f'throughput to N (tests/s)', fmt(thr_a), fmt(thr_b), ratio(thr_a, thr_b) + ' (A/B)'),
        ('steady tests/s',        fmt(A['steady']),          fmt(B['steady']),       ratio(A['steady'], B['steady']) + ' (A/B)'),
        ('steady tests/min',      fmt((A['steady'] or 0)*60), fmt((B['steady'] or 0)*60), ''),
        ('final avgTestMs',       fmt(A['final_avg']),       fmt(B['final_avg']),    ratio(B['final_avg'], A['final_avg']) + ' (B slower if >1)'),
        ('final maxTestMs',       fmt(A['final_max']),       fmt(B['final_max']),    ''),
        ('final timeouts',        A['final_timeout'],        B['final_timeout'],     ''),
        ('peak stuckSlots',       A['peak_stuck'],           B['peak_stuck'],        ''),
    ]

    w = 26
    print(f'\n{"metric":<{w}} {la:>16} {lb:>16}   comparison')
    print('-' * (w + 16 + 16 + 20))
    for name, a, b, cmp in rows:
        print(f'{name:<{w}} {str(a):>16} {str(b):>16}   {cmp}')
    print()

    if thr_a and thr_b:
        r = thr_a / thr_b
        faster, slower = (la, lb) if r >= 1 else (lb, la)
        print(f'VERDICT: {faster} processed {n} tests {max(r,1/r):.1f}x faster than {slower} '
              f'(equal-work wall-clock). ', end='')
        # crude driver hint
        if (B['final_timeout'] or 0) > 10 * (A['final_timeout'] or 0) + 10:
            print(f'{lb} timeouts ({B["final_timeout"]}) vs {la} ({A["final_timeout"]}) — stuck/hung tests are a major factor.')
        elif A['final_avg'] and B['final_avg'] and B['final_avg'] > 1.5 * A['final_avg']:
            print(f'per-test latency {B["final_avg"]}ms vs {A["final_avg"]}ms — {lb} is slower per test, not just fewer slots.')
        else:
            print('check the per-test latency + stuckSlots columns for the driver.')
    print()

if __name__ == '__main__':
    main()
