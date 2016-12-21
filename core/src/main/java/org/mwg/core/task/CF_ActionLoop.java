package org.mwg.core.task;

import org.mwg.Callback;
import org.mwg.Constants;
import org.mwg.plugin.SchedulerAffinity;
import org.mwg.task.Action;
import org.mwg.task.Task;
import org.mwg.task.TaskContext;
import org.mwg.task.TaskResult;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class CF_ActionLoop extends CF_Action {

    private final String _lower;
    private final String _upper;
    private final Task _subTask;

    CF_ActionLoop(final String p_lower, final String p_upper, final Task p_subTask) {
        super();
        this._subTask = p_subTask;
        this._lower = p_lower;
        this._upper = p_upper;
    }

    @Override
    public void eval(final TaskContext ctx) {
        final String lowerString = ctx.template(_lower);
        final String upperString = ctx.template(_upper);
        final int lower = (int) Double.parseDouble(ctx.template(lowerString));
        final int upper = (int) Double.parseDouble(ctx.template(upperString));
        final TaskResult previous = ctx.result();
        final CF_ActionLoop selfPointer = this;
        final AtomicInteger cursor = new AtomicInteger(lower);
        if ((upper - lower) >= 0) {
            final Callback[] recursiveAction = new Callback[1];
            recursiveAction[0] = new Callback<TaskResult>() {
                @Override
                public void on(final TaskResult res) {
                    final int current = cursor.getAndIncrement();
                    if (res != null) {
                        res.free();
                    }
                    if (current > upper) {
                        ctx.continueTask();
                    } else {
                        //recursive call
                        selfPointer._subTask.executeFromUsing(ctx, previous, SchedulerAffinity.SAME_THREAD, new Callback<TaskContext>() {
                            @Override
                            public void on(TaskContext result) {
                                result.defineVariable("i", current);
                            }
                        }, recursiveAction[0]);
                    }
                }
            };
            _subTask.executeFromUsing(ctx, previous, SchedulerAffinity.SAME_THREAD, new Callback<TaskContext>() {
                @Override
                public void on(TaskContext result) {
                    result.defineVariable("i", cursor.getAndIncrement());
                }
            }, recursiveAction[0]);
        } else {
            ctx.continueTask();
        }
    }

    @Override
    public Task[] children() {
        Task[] children_tasks = new Task[1];
        children_tasks[0] = _subTask;
        return children_tasks;
    }

    @Override
    public void cf_serialize(StringBuilder builder, Map<Integer, Integer> dagIDS) {
        builder.append(ActionNames.LOOP);
        builder.append(Constants.TASK_PARAM_OPEN);
        TaskHelper.serializeString(_lower, builder);
        builder.append(Constants.TASK_PARAM_SEP);
        TaskHelper.serializeString(_upper, builder);
        builder.append(Constants.TASK_PARAM_SEP);
        final CoreTask castedAction = (CoreTask) _subTask;
        final int castedActionHash = castedAction.hashCode();
        if (dagIDS == null || !dagIDS.containsKey(castedActionHash)) {
            castedAction.serialize(builder, dagIDS);
        } else {
            builder.append("" + dagIDS.get(castedActionHash));
        }
        builder.append(Constants.TASK_PARAM_CLOSE);
    }

}