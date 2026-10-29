# Task system

A Task owns one injected Action and zero or more prerequisite child Tasks. A parent
may start only when all children succeed. Cycles, duplicate IDs, and reparenting
are rejected. Build the full hierarchy before submitting it. A failed/cancelled
child fails the parent; independent tasks can continue.

| State | Meaning | Next states |
| --- | --- | --- |
| PENDING | Queued or suspended | RUNNING, BLOCKED, FAILED, CANCELLED |
| RUNNING | Action started | SUCCESS, BLOCKED, FAILED, CANCELLED, PENDING |
| BLOCKED | Waiting or verification/budget failure | PENDING via explicit recovery, CANCELLED |
| SUCCESS | Observed postcondition verified | terminal |
| FAILED | Exception or failed prerequisite | terminal |
| CANCELLED | Token cancelled and cleanup attempted | terminal |

Public lifecycle methods are can_start, start, tick, is_complete, verify,
get_failure_reason, recover, and cancel. Scheduler.tick is the normal execution
entry point. Do not mutate statuses directly; the internal transition helper
publishes TaskChanged for observability. Parent waiting is normally represented
by a pending task that is ineligible for scheduling.

An action's completion signal only triggers verify(snapshot). False verification
blocks the task and stops the action; attempting an operation never proves success.
Exceptions fail the task with their type and message. A running action reaching
max_ticks blocks. A blocked action is never automatically repeated.

Recovery requires an explicitly supplied replacement strategy and a remaining
max_retries budget (default zero). It preserves identity and resets the action
attempt budget. Automatic diagnosis and alternative-strategy selection are Phase
6, not claimed here. Recovery cannot revive terminal tasks.

Higher-priority tasks interrupt lower-priority actions by cancelling their inputs
and returning the original task to pending. This is suspension, not terminal
cancellation. Resume restarts the action with the same token and current snapshot.
No survival classifier is implemented yet.

Goal/task cancellation propagates through child tokens and calls every child
cleanup. Controller.stop pauses scheduling, cancels all registered tasks, and
always invokes the host input release callback, including when cleanup raises.
Tokens alone are cooperative; use the controller emergency-stop entry point to
release active inputs immediately. Completed tasks retain their verified status.
