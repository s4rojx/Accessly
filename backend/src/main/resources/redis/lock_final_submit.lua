-- lock_final_submit.lua
-- Atomically transitions attempt status to SUBMITTED and harvests all answer state.
-- Guarantees exactly-once submission: the status check + flip is a single atomic operation,
-- making it impossible for a user click and the server timer reaper to both succeed.
--
-- KEYS[1] = attempt:{examId:studentId}:meta
-- KEYS[2] = attempt:{examId:studentId}:state
-- ARGV[1] = submittedAtEpochMs  (wall-clock epoch in milliseconds)
-- ARGV[2] = submissionSource    ('USER_CLICK' | 'TIMER_REAPER')
--
-- Return values:
--   List (HGETALL of :state) - submission accepted; caller must persist to PostgreSQL
--   {err="ALREADY_SUBMITTED"} - attempt was already closed; caller must handle idempotently

local status = redis.call('HGET', KEYS[1], 'status')
if status ~= 'IN_PROGRESS' then
    return redis.error_reply('ALREADY_SUBMITTED')
end

-- Atomically flip status before reading answers (no other caller can win past this point)
redis.call('HSET', KEYS[1],
    'status',           'SUBMITTED',
    'submittedAtEpoch', ARGV[1],
    'source',           ARGV[2])

-- Return flat key-value list: [questionId1, stateJson1, questionId2, stateJson2, ...]
local allAnswers = redis.call('HGETALL', KEYS[2])
return allAnswers
