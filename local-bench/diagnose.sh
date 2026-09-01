#!/usr/bin/env bash
# Live bottleneck sampler for a running mini-testing JVM — replaces the manual
# jstack + grep + kafka-consumer-groups dance. Every INTERVAL seconds it prints one line:
#   time | cpu% | rate done% avgMs stuck tmout err | worker buckets | states | kafkaLag
#
# Worker buckets classify each mini-test-worker by its top frame:
#   io       = network wait (Net.poll / socketRead / NioSocketImpl / SSL read)
#   connLock = okhttp connection-pool contention (callAcquirePooledConnection / RealConnectionPool)
#   cpu      = resolveWordListVar / regex / String hashing
#   park     = idle (Unsafe.park)
#   other    = anything else
# states = thread-state histogram for worker+pc pools: RUNNABLE/WAITING/TIMED/BLOCKED (R/W/T/B).
# kafkaLag = committed-offset lag on the test topic (how many produced records are not yet committed).
#
# AUTO HANG-CAPTURE: if `done` doesn't advance for STALL_TICKS intervals while the run is still
# active, it writes a hang-<ts>.txt bundle (3 stack dumps 4s apart + thread-state histogram +
# BLOCKED lock-owner chain + kafka offsets) — the reproducible RCA artifact for the 40% stall.
#
# Usage: local-bench/diagnose.sh [interval_sec]   (default 20; runs until the JVM exits)
set -uo pipefail
INTERVAL="${1:-20}"
REPO=/Users/mann/workspace/akto
cd "$REPO"
JSTACK="$(/usr/libexec/java_home -v 17 2>/dev/null)/bin/jstack"
JAR_NAME="mini-testing-1.0-SNAPSHOT-jar"
KAFKA_CTR="${KAFKA_CTR:-kafka-internal}"
KAFKA_GROUP="${KAFKA_GROUP:-testing-group}"
# done must be flat for this many consecutive intervals (while active) before we grab a hang bundle.
STALL_TICKS="${STALL_TICKS:-3}"

hdr() { printf "%-8s %6s | %6s %5s %7s %5s %5s %6s | %-22s | %-13s | %s\n" \
  time cpu% rate done avgMs stuck tmout err "workers io/cl/cpu/pk/ot" "states R/W/T/B" kafkaLag; }

# committed-offset lag on the topic the consumer group is draining (sum over partitions)
kafka_lag() {
  docker exec "$KAFKA_CTR" kafka-consumer-groups --bootstrap-server localhost:9092 \
      --describe --group "$KAFKA_GROUP" 2>/dev/null \
    | awk 'NR>1 && $6 ~ /^[0-9-]+$/ {cur+=$4; end+=$5; lag+=$6}
           END{ if(end>0) printf "cur=%d/end=%d lag=%d", cur, end, lag; else printf "n/a"; }'
}

# error-bucket breakdown of the current run log (cumulative counts by type)
err_breakdown() {
  local log="$1"; [ -f "$log" ] || return
  awk '
    /ERROR|Exception/ {
      if      ($0 ~ /Unexpected char .* in x-request-id/)                   h++
      else if ($0 ~ /cannot loop on the given parameter|unable to resolve/) rp++
      else if ($0 ~ /fetchTestScript/)                                      ts++
      else if ($0 ~ /findStiByParam|Cannot construct instance/)             sti++
      else if ($0 ~ /ultron\.akto\.io.*(timeout|SocketTimeout)|SocketTimeoutException/) ab++
      else if ($0 ~ /Illegal character in path|URISyntaxException/)         uri++
      else if ($0 ~ /Invalid method name/)                                  im++
      else if ($0 ~ /Index [0-9]+ out of bounds/)                           oob++
      else if ($0 ~ /ERROR/)                                                oth++
    }
    END{printf "  errors: headerChar=%d resolveParam=%d scriptNpe=%d stiDeser=%d abstractorTimeout=%d uriSyntax=%d invalidMethod=%d indexOOB=%d otherERROR=%d\n",
        h+0, rp+0, ts+0, sti+0, ab+0, uri+0, im+0, oob+0, oth+0}' "$log"
}

# Full RCA bundle grabbed once when the run stalls: 3 dumps to see if threads are genuinely
# stuck (same frame across all 3) vs merely slow, plus who owns the monitor everyone waits on.
hang_capture() {
  local pid="$1"; local out; out="local-bench/hang-$(date +%Y%m%d-%H%M%S).txt"
  echo ">> STALL detected — capturing hang bundle to $out" | tee -a "$out"
  {
    echo "=== HANG CAPTURE pid=$pid $(date) ==="
    echo "=== kafka: $(kafka_lag) (group=$KAFKA_GROUP) ==="
    for k in 1 2 3; do
      "$JSTACK" "$pid" 2>/dev/null > "/tmp/hang.$$.$k.txt"
      echo; echo "--- dump $k: worker+pc thread-state histogram ---"
      awk '/^"(mini-test-worker|pc-)/{getline s; if (s ~ /State:/){sub(/.*State: /,"",s); split(s,a," "); c[a[1]]++}} END{for(k in c) printf "  %-14s %d\n", k, c[k]}' "/tmp/hang.$$.$k.txt"
      [ "$k" -lt 3 ] && perl -e 'select(undef,undef,undef,4)'
    done

    echo; echo "--- top frame of mini-test-worker threads, dump 1 vs 3 (identical => genuinely stuck) ---"
    for k in 1 3; do
      echo "  [dump $k]"
      awk '/^"mini-test-worker/{f=1;name=$1;next} f&&/^\t*at /{sub(/^\t*at /,""); print "    "$0; f=0}' "/tmp/hang.$$.$k.txt" \
        | sed -E 's/[@0-9a-fx]+//g; s/\(.*\)//' | sort | uniq -c | sort -rn | head -8
    done

    echo; echo "--- BLOCKED lock-owner chain (the monitor everyone is waiting on, and who holds it) ---"
    awk '
      /waiting to lock <0x/ { match($0,/<0x[0-9a-f]+>/); a=substr($0,RSTART,RLENGTH); waits[a]++ }
      END{ mx=0; for(k in waits) if(waits[k]>mx){mx=waits[k]; hot=k}
           if(hot!="") print "  most-contended monitor "hot" — "mx" threads waiting"; else print "  (no BLOCKED-on-monitor threads)" }
    ' "/tmp/hang.$$.1.txt"
    HOT=$(awk '/waiting to lock <0x/{match($0,/<0x[0-9a-f]+>/);a=substr($0,RSTART,RLENGTH);w[a]++} END{mx=0;for(k in w)if(w[k]>mx){mx=w[k];h=k};print h}' "/tmp/hang.$$.1.txt")
    if [ -n "$HOT" ]; then
      echo "  --- owner stanza (thread holding $HOT) ---"
      awk -v hot="$HOT" 'BEGIN{RS=""} $0 ~ ("locked "hot) && $0 !~ ("waiting to lock "hot){print; exit}' "/tmp/hang.$$.1.txt" | sed 's/^/    /' | head -25
    fi
    echo; echo "=== end hang capture ==="
  } >> "$out" 2>&1
  rm -f /tmp/hang.$$.1.txt /tmp/hang.$$.2.txt /tmp/hang.$$.3.txt
  echo ">> hang bundle written: $out"
}

hdr
i=0; prev_done=""; flat=0; captured=0
while true; do
  PID=$(pgrep -f "$JAR_NAME" | head -1)
  [ -z "$PID" ] && { echo ">> no running mini-testing JVM — done"; break; }
  CPU=$(ps -o %cpu= -p "$PID" 2>/dev/null | tr -d ' ')
  LOG=$(ls -t local-bench/run-*.log 2>/dev/null | head -1)
  P=$(grep -a 'TESTRUN PROGRESS' "$LOG" 2>/dev/null | tail -1)
  rate=$(echo "$P"  | grep -oE 'rate=[0-9.]+'        | cut -d= -f2)
  donep=$(echo "$P" | grep -oE '\([0-9]+%\)'         | tr -d '()' | head -1)
  donecnt=$(echo "$P" | grep -oE 'done=[0-9]+'       | head -1 | cut -d= -f2)
  avg=$(echo "$P"   | grep -oE 'avgTestMs=[0-9]+'    | cut -d= -f2)
  stuck=$(echo "$P" | grep -oE 'stuckSlots=[0-9]+'   | cut -d= -f2)
  tmout=$(echo "$P" | grep -oE ' timeout=[0-9]+'     | grep -oE '[0-9]+')
  err=$(echo "$P"   | grep -oE ' err=[0-9]+'         | grep -oE '[0-9]+')

  "$JSTACK" "$PID" 2>/dev/null > /tmp/diag.$$.txt
  buckets=$(awk '
    /^"mini-test-worker/{p=1;next}
    p && /^\t*at /{
      if      ($0 ~ /Net\.poll|socketRead|NioSocketImpl|SSLSocketImpl.*read/)          io++
      else if ($0 ~ /callAcquirePooledConnection|RealConnectionPool/)                   cl++
      else if ($0 ~ /resolveWordListVar|java\.util\.regex|StringLatin1|Pattern/)        cpu++
      else if ($0 ~ /Unsafe\.park/)                                                     pk++
      else                                                                             ot++
      p=0
    }
    END{printf "%d/%d/%d/%d/%d", io+0, cl+0, cpu+0, pk+0, ot+0}' /tmp/diag.$$.txt)
  # thread-state histogram for worker+pc pools (RUNNABLE/WAITING/TIMED_WAITING/BLOCKED)
  states=$(awk '
    /^"(mini-test-worker|pc-)/{getline s; if (s ~ /State:/){sub(/.*State: /,"",s); split(s,a," ");
      st=a[1]; if(st=="RUNNABLE")r++; else if(st=="WAITING")w++; else if(st=="TIMED_WAITING")t++; else if(st=="BLOCKED")b++}}
    END{printf "%d/%d/%d/%d", r+0, w+0, t+0, b+0}' /tmp/diag.$$.txt)
  rm -f /tmp/diag.$$.txt
  lag=$(kafka_lag)

  printf "%-8s %6s | %6s %5s %7s %5s %5s %6s | %-22s | %-13s | %s\n" \
    "$(date +%H:%M:%S)" "${CPU:-?}" "${rate:-?}" "${donep:-?}" "${avg:-?}" "${stuck:-?}" "${tmout:-?}" "${err:-?}" \
    "$buckets" "$states" "$lag"

  # ---- stall detection: done flat for STALL_TICKS while the run is still active ----
  if [ -n "${donecnt:-}" ]; then
    if [ "$donecnt" = "$prev_done" ]; then flat=$((flat+1)); else flat=0; captured=0; fi
    prev_done="$donecnt"
    if [ "$flat" -ge "$STALL_TICKS" ] && [ "$captured" -eq 0 ]; then
      hang_capture "$PID"; captured=1
    fi
  fi

  i=$((i+1))
  if [ $((i % 10)) -eq 0 ]; then err_breakdown "$LOG"; hdr; fi
  perl -e "select(undef,undef,undef,$INTERVAL)"
done
