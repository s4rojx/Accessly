-- save_question_action.lua
-- Atomically validates exam state and persists a single question interaction.
--
-- KEYS[1] = attempt:{examId:studentId}:meta
-- KEYS[2] = attempt:{examId:studentId}:state
-- ARGV[1] = examId          (unused in logic; present for documentation)
-- ARGV[2] = questionId      (hash field in KEYS[2])
-- ARGV[3] = clientSequenceNum (monotonically increasing per student per question)
-- ARGV[4] = newStateJson    ({"h":"ANSWERED","c":[1],"t":14500,"seq":3})
--
-- Return values:
--   "OK"                    - state written successfully
--   "IGNORED_STALE_PACKET"  - clientSeq is <= existing seq; idempotent no-op
--   {err="ATTEMPT_NOT_FOUND"}          - meta key absent (Redis eviction / crash)
--   {err="ATTEMPT_ALREADY_SUBMITTED"}  - attempt status is not IN_PROGRESS
--   {err="EXAM_TIME_EXPIRED"}          - server clock is past effectiveEndEpoch

local status = redis.call('HGET', KEYS[1], 'status')
if not status then
    return redis.error_reply('ATTEMPT_NOT_FOUND')
end
if status ~= 'IN_PROGRESS' then
    return redis.error_reply('ATTEMPT_ALREADY_SUBMITTED')
end

-- Use Redis server clock as authoritative timer (immune to client/app-server clock skew)
local effectiveEnd = tonumber(redis.call('HGET', KEYS[1], 'effectiveEndEpoch'))
local t = redis.call('TIME')
local nowMs = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
if nowMs > effectiveEnd then
    return redis.error_reply('EXAM_TIME_EXPIRED')
end

-- Reject out-of-order network packets without overwriting a newer state
local existingRaw = redis.call('HGET', KEYS[2], ARGV[2])
if existingRaw then
    local existing = cjson.decode(existingRaw)
    if existing.seq and tonumber(ARGV[3]) <= tonumber(existing.seq) then
        return 'IGNORED_STALE_PACKET'
    end
end

redis.call('HSET', KEYS[2], ARGV[2], ARGV[4])
return 'OK'
