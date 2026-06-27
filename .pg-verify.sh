#!/usr/bin/env bash
# PulseGate end-to-end verification. Runs inside WSL (native docker + localhost:8081).
API=http://localhost:8081
PG="docker exec pulsegate-postgres-1 psql -U pulsegate_user -d pulsegate -t -A -q"
pass=0; fail=0
chk() { if [ "$2" = "$3" ]; then echo "  PASS  $1 (got: $3)"; pass=$((pass+1));
        else echo "  FAIL  $1 (expected: $2, got: $3)"; fail=$((fail+1)); fi; }

echo "=== waiting for app health ==="
for i in $(seq 1 20); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' $API/actuator/health)" = "200" ] && { echo "  app healthy"; break; }
  sleep 2
done

echo
echo "=== TIMEZONE PROOF ==="
echo "  container clock   : $(docker exec pulsegate-pulsegate-1 date '+%Y-%m-%d %H:%M:%S %Z')"
echo "  JVM user.timezone : $(docker exec pulsegate-pulsegate-1 java -XshowSettings:properties -version 2>&1 | grep user.timezone | tr -s ' ')"

echo
echo "=== clean slate (remove debug test rows) ==="
$PG -c "TRUNCATE jobs;" >/dev/null && echo "  jobs table truncated"

echo
echo "=== 1. POST /api/jobs  (submit) ==="
code=$(curl -s -o /tmp/b1 -w '%{http_code}' -X POST $API/api/jobs -H 'Content-Type: application/json' -d '{"type":"EMAIL","payload":{"to":"a@b.com"},"priority":5}')
ID=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' /tmp/b1)
chk "submit returns 202 Accepted" 202 "$code"
echo "  -> new job id: $ID"
sleep 1
echo "  -> stored created_at: $($PG -c "SELECT created_at FROM jobs WHERE id='$ID';")   <== should match YOUR wall clock"

echo
echo "=== 2. GET /api/jobs/{id}  (status of one) ==="
chk "get one job 200" 200 "$(curl -s -o /dev/null -w '%{http_code}' $API/api/jobs/$ID)"

echo
echo "=== 3. GET /api/jobs  (list, paginated) ==="
chk "list jobs 200" 200 "$(curl -s -o /dev/null -w '%{http_code}' "$API/api/jobs?page=0&size=20")"

echo
echo "=== 4. DELETE /api/jobs/{id}  (cancel a PENDING job) ==="
PID=$($PG -c "INSERT INTO jobs (type,payload,status,priority,attempts,max_attempts) VALUES ('EMAIL','{}'::jsonb,'PENDING',5,0,3) RETURNING id;" | head -1 | tr -d '[:space:]')
chk "cancel pending 200" 200 "$(curl -s -o /dev/null -w '%{http_code}' -X DELETE $API/api/jobs/$PID)"

echo
echo "=== 5. GET /api/stats  (snapshot) ==="
chk "stats 200" 200 "$(curl -s -o /tmp/s -w '%{http_code}' $API/api/stats)"
echo "  -> $(cat /tmp/s)"

echo
echo "=== 6. GET /api/stats/stream  (SSE live feed) ==="
n=$(curl -s --max-time 3 -N $API/api/stats/stream | grep -c 'event:stats')
[ "$n" -ge 1 ] && { echo "  PASS  SSE pushed $n stats frame(s) in 3s"; pass=$((pass+1)); } || { echo "  FAIL  no SSE frames"; fail=$((fail+1)); }

echo
echo "=== 7. GET /actuator/prometheus  (metrics) ==="
chk "prometheus 200" 200 "$(curl -s -o /tmp/m -w '%{http_code}' $API/actuator/prometheus)"
for m in pulsegate_queue_depth pulsegate_jobs_processed_total pulsegate_worker_active_count; do
  grep -q "$m" /tmp/m && { echo "  PASS  metric $m present"; pass=$((pass+1)); } || { echo "  FAIL  metric $m missing"; fail=$((fail+1)); }
done

echo
echo "=== 8. GET /actuator/health ==="
chk "health UP" '{"status":"UP"}' "$(curl -s $API/actuator/health)"

echo
echo "=== 9. GET /api/dead-letter  (list dead jobs) ==="
DID=$($PG -c "INSERT INTO jobs (type,payload,status,priority,attempts,max_attempts,error_message) VALUES ('WEBHOOK','{}'::jsonb,'DEAD',5,3,3,'seeded dead job') RETURNING id;" | head -1 | tr -d '[:space:]')
n=$(curl -s $API/api/dead-letter | grep -c "$DID")
chk "dead-letter lists the seeded DEAD job" 1 "$n"

echo
echo "=== 10. POST /api/dead-letter/{id}/retry  (requeue a dead job) ==="
chk "retry dead 200" 200 "$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/dead-letter/$DID/retry)"
sleep 1
st=$($PG -c "SELECT status FROM jobs WHERE id='$DID';" | tr -d ' ')
[ "$st" != "DEAD" ] && { echo "  PASS  job left dead-letter (now: $st)"; pass=$((pass+1)); } || { echo "  FAIL  still DEAD"; fail=$((fail+1)); }

echo
echo "==========================================="
echo "   RESULT:  $pass passed,  $fail failed"
echo "==========================================="
