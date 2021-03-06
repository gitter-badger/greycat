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
package greycat;

import greycat.utility.Tuple;

public interface TaskContext {

    Graph graph();

    long world();

    TaskContext setWorld(long world);

    long time();

    TaskContext setTime(long time);

    Tuple<String, TaskResult>[] variables();

    TaskResult variable(String name);

    boolean isGlobal(String name);

    TaskResult wrap(Object input);

    TaskResult wrapClone(Object input);

    TaskResult newResult();

    TaskContext declareVariable(String name);

    TaskContext defineVariable(String name, Object initialResult);

    TaskContext defineVariableForSubTask(String name, Object initialResult);

    TaskContext setGlobalVariable(String name, Object value);

    TaskContext setVariable(String name, Object value);

    TaskContext addToGlobalVariable(String name, Object value);

    TaskContext addToVariable(String name, Object value);

    //Object based results
    TaskResult result();

    TaskResult<Node> resultAsNodes();

    TaskResult<String> resultAsStrings();

    void continueTask();

    void continueWith(TaskResult nextResult);

    void endTask(TaskResult nextResult, Exception e);

    String template(String input);

    String[] templates(String[] inputs);

    void append(String additionalOutput);

}
