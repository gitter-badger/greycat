/**
 * Copyright 2017 The GreyCat Authors.  All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package greycat.internal.task;

import greycat.ActionFunction;
import greycat.TaskContext;
import greycat.TaskFunctionSelectObject;
import greycat.Tasks;
import org.junit.Assert;
import org.junit.Test;

public class ActionSelectObjectTest extends AbstractActionTest {

    @Test
    public void testSelectOneObject() {
        initGraph();
        startMemoryLeakTest();
        Tasks.newTask()
                .then(CoreActions.inject(55))
                .then(CoreActions.selectObject((object, context) -> false))
                .thenDo(context -> Assert.assertEquals(context.result().size(), 0))
                .execute(graph, null);

        Tasks.newTask()
                .then(CoreActions.inject(55))
                .then(CoreActions.selectObject(new TaskFunctionSelectObject() {
                    @Override
                    public boolean select(Object object, TaskContext context) {
                        return true;
                    }
                }))
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        Assert.assertNotEquals(ctx.result().size(), 0);
                        Assert.assertEquals(55, ctx.result().get(0));
                    }
                })
                .execute(graph, null);

        Tasks.newTask()
                .then(CoreActions.inject(55))
                .then(CoreActions.selectObject(new TaskFunctionSelectObject() {
                    @Override
                    public boolean select(Object object, TaskContext context) {
                        return (Integer) object == 55;
                    }
                }))
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        Assert.assertNotEquals(ctx.result().size(), 0);
                        Assert.assertEquals(55, ctx.result().get(0));
                    }
                })
                .execute(graph, null);

        endMemoryLeakTest();
        removeGraph();
    }

    /*
    @Test
    public void testSelectObjectPrimitiveArray() {
        int[] toInject = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        initGraph();
        startMemoryLeakTest();
        newTask().inject(toInject)
                .selectObject(new TaskFunctionSelectObject() {
                    @Override
                    public boolean select(Object object, TaskContext context) {
                        return false;
                    }
                })
                .then(new ActionFunction() {
                    @Override
                    public void eval(TaskContext context) {
                        Object[] objects = (Object[]) context.result();
                        Assert.assertNotNull(context.result());
                        Assert.assertEquals(0, objects.length);
                    }
                })
                .execute(graph, null);

        newTask().inject(toInject)
                .selectObject(new TaskFunctionSelectObject() {
                    @Override
                    public boolean select(Object object, TaskContext context) {
                        return true;
                    }
                })
                .then(new ActionFunction() {
                    @Override
                    public void eval(TaskContext context) {
                        Assert.assertNotNull(context.result());
                        TaskResult<Double> doubleResult = context.result();
                        for (int i = 0; i < toInject.length; i++) {
                            Assert.assertEquals(toInject[i]+"", doubleResult.get(i)+"");
                        }
                    }
                })
                .execute(graph, null);

        newTask().inject(toInject)
                .selectObject(new TaskFunctionSelectObject() {
                    @Override
                    public boolean select(Object object, TaskContext context) {
                        return (Integer) object % 2 == 0;
                    }
                })
                .then(new ActionFunction() {
                    @Override
                    public void eval(TaskContext context) {
                        Assert.assertNotNull(context.result());
                        Object[] evenNumbers = new Object[]{2, 4, 6, 8, 10};
                        Assert.assertArrayEquals(evenNumbers, (Object[]) context.result());
                    }
                })
                .execute(graph, null);

        endMemoryLeakTest();
        removeGraph();
    }

    @Test
    public void testSelectObjectArray() {
        Integer[] toInject = new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        initGraph();
        startMemoryLeakTest();
        newTask().inject(toInject)
                .selectObject(new TaskFunctionSelectObject() {
                    @Override
                    public boolean select(Object object, TaskContext context) {
                        return false;
                    }
                })
                .then(new ActionFunction() {
                    @Override
                    public void eval(TaskContext context) {
                        Object[] objects = (Object[]) context.result();
                        Assert.assertNotNull(context.result());
                        Assert.assertEquals(0, objects.length);
                        context.setUnsafeResult(null);
                    }
                })
                .execute(graph, null);

        newTask().inject(toInject)
                .selectObject(new TaskFunctionSelectObject() {
                    @Override
                    public boolean select(Object object, TaskContext context) {
                        return true;
                    }
                })
                .then(new ActionFunction() {
                    @Override
                    public void eval(TaskContext context) {
                        Assert.assertNotNull(context.result());
                        Assert.assertArrayEquals(toInject, (Object[]) context.result());
                        context.setUnsafeResult(null);
                    }
                })
                .execute(graph, null);

        newTask().inject(toInject)
                .selectObject(new TaskFunctionSelectObject() {
                    @Override
                    public boolean select(Object object, TaskContext context) {
                        return (Integer) object % 2 == 0;
                    }
                })
                .then(new ActionFunction() {
                    @Override
                    public void eval(TaskContext context) {
                        Assert.assertNotNull(context.result());
                        Integer[] evenNumbers = new Integer[]{2, 4, 6, 8, 10};
                        Assert.assertArrayEquals(evenNumbers, (Object[]) context.result());
                        context.setUnsafeResult(null);
                    }
                })
                .execute(graph, null);

        endMemoryLeakTest();
        removeGraph();
    }

    @Test
    public void testSelectComplexObjectArray() {
        initGraph();

        final Object[] innerTab = new Object[3];
        innerTab[0] = 55;
        final Node n1 = graph.newNode(0, 0);
        final Node n2 = graph.newNode(0, 0);
        innerTab[1] = n1;
        innerTab[2] = n2;

        final Object[] toInject = new Object[3];
        final Node n3 = graph.newNode(0, 0);
        toInject[0] = n3;
        toInject[1] = innerTab;
        toInject[2] = "A simple String";

        startMemoryLeakTest();

        newTask().inject(toInject)
                .selectObject(new TaskFunctionSelectObject() {
                    @Override
                    public boolean select(Object object) {
                        return false;
                    }
                })
                .then(new ActionFunction() {
                    @Override
                    public void eval(TaskContext context) {
                        Object[] objects = (Object[]) context.result();
                        Assert.assertNotNull(context.result());
                        Assert.assertEquals(0, objects.length);
                        context.setUnsafeResult(null);
                    }
                })
                .execute(graph, null);

        newTask().inject(toInject)
                .selectObject(new TaskFunctionSelectObject() {
                    @Override
                    public boolean select(Object object) {
                        return true;
                    }
                })
                .then(new ActionFunction() {
                    @Override
                    public void eval(TaskContext context) {
                        ActionSelectObjectTest.this.compareArray(toInject, (Object[]) context.result());
                        context.setUnsafeResult(null);
                    }
                })
                .execute(graph, null);


        newTask().inject(toInject)
                .selectObject(new TaskFunctionSelectObject() {
                    @Override
                    public boolean select(Object object) {
                        return object instanceof BaseNode;
                    }
                })
                .then(new ActionFunction() {
                    @Override
                    public void eval(TaskContext context) {
                        Object[] result = new Object[2];
                        result[0] = n3;
                        Object[] insideResult = new Object[2];
                        insideResult[0] = n1;
                        insideResult[1] = n2;
                        result[1] = insideResult;

                        ActionSelectObjectTest.this.compareArray(result, (Object[]) context.result());

                        context.setUnsafeResult(null);

                    }
                })
                .execute(graph, null);

        n1.free();
        n2.free();
        n3.free();

        endMemoryLeakTest();

        removeGraph();
    }

    private void compareArray(Object[] expected, Object[] result) {
        Assert.assertNotNull(result);
        Assert.assertEquals(expected.length, result.length);
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] instanceof Object[]) {
                compareArray((Object[]) expected[i], (Object[]) result[i]);
            } else if (expected[i] instanceof BaseNode) {
                Assert.assertEquals(((Node) expected[i]).id(), ((Node) result[i]).id());
            } else {
                Assert.assertEquals(expected[i], result[i]);
            }
        }
    }
    */
    //TODO LUDO :-)

}
