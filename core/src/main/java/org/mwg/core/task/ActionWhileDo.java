package org.mwg.core.task;

import org.mwg.task.Task;
import org.mwg.task.TaskAction;
import org.mwg.task.TaskContext;
import org.mwg.task.TaskFunctionConditional;

import java.util.concurrent.atomic.AtomicInteger;

class ActionWhileDo implements TaskAction {

    private final TaskFunctionConditional _cond;

    private final Task _then;

    ActionWhileDo(final TaskFunctionConditional p_cond, final Task p_then) {
        _cond = p_cond;
        _then = p_then;
    }

    @Override
    public void eval(TaskContext context) {
        /*
        TaskAction task = new TaskAction() {
            @Override
            public void eval(TaskContext subContext) {

            }
        };
        if (_cond.eval(context)) {
            _then.executeThenAsync(context, context.getPreviousResult(), );
        } else {
            context.setResult(context.getPreviousResult());
            context.next();
        }


        final Object[] castedResult = convert(context.getPreviousResult());
        AtomicInteger cursor = new AtomicInteger(0);
        final TaskContext[] results = new TaskContext[castedResult.length];
        _then.executeThenAsync(context, castedResult[0], new TaskAction() {
            @Override
            public void eval(final TaskContext subTaskFinalContext) {
                int current = cursor.getAndIncrement();
                results[current] = subTaskFinalContext;
                int nextCursot = current + 1;
                if (nextCursot == results.length) {
                    context.setResult(results);
                    context.next();
                } else {
                    //recursive call
                    _subTask.executeThenAsync(context, castedResult[nextCursot], this);
                }
            }
        });
        */
    }

}