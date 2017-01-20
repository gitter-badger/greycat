package org.mwg.structure.tree;

import org.mwg.*;
import org.mwg.base.BaseNode;
import org.mwg.internal.task.CoreActions;
import org.mwg.plugin.Job;
import org.mwg.plugin.NodeState;
import org.mwg.struct.Relation;
import org.mwg.structure.distance.Distance;
import org.mwg.structure.distance.Distances;
import org.mwg.structure.distance.EuclideanDistance;
import org.mwg.structure.distance.GeoDistance;
import org.mwg.structure.util.HRect;
import org.mwg.structure.util.NearestNeighborArrayList;
import org.mwg.structure.util.NearestNeighborList;
import org.mwg.task.*;
import org.mwg.utility.Enforcer;

import static org.mwg.internal.task.CoreActions.*;
import static org.mwg.task.Tasks.emptyResult;
import static org.mwg.task.Tasks.newTask;

@SuppressWarnings("Duplicates")
public class KDTreeOld extends BaseNode {

    public static final String NAME = "KDTreeOld";
    public static final String FROM = "from";
    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    public static final String KEY = "key";
    public static final String VALUE = "value";
    public static final String SIZE = "size";
    public static final String DIMENSIONS = "dimensions";
    public static final String DISTANCE = "distance";
    public static final String DISTANCE_THRESHOLD = "threshold";       //Distance threshold to define when 2 keys are not considered the same anymopre
    public static final double DISTANCE_THRESHOLD_DEF = 1e-10;
    public static final int DISTANCE_TYPE_DEF = 0;

    public KDTreeOld(long p_world, long p_time, long p_id, Graph p_graph) {
        super(p_world, p_time, p_id, p_graph);
    }

    //Insert key/value task
    private static Task insert = newTask()
            .whileDo(new ConditionalFunction() {
                         @Override
                         public boolean eval(TaskContext ctx) {
                             Node current = ctx.resultAsNodes().get(0);
                             double[] nodeKey = (double[]) current.get(KEY);
                             //Get variables from context
                             //toDo optimize the variables here
                             int dim = (int) ctx.variable("dim").get(0);
                             double[] keyToInsert = (double[]) ctx.variable("key").get(0);
                             Node valueToInsert = (Node) ctx.variable("value").get(0);
                             Node root = (Node) ctx.variable("root").get(0);
                             Distance distance = (Distance) ctx.variable("distance").get(0);
                             double err = (double) ctx.variable("err").get(0);
                             int lev = (int) ctx.variable("lev").get(0);

                             //Bootstrap, first insert ever
                             if (nodeKey == null) {
                                 current.set(DIMENSIONS, Type.INT, dim);
                                 current.set(KEY, Type.DOUBLE_ARRAY, keyToInsert);
                                 ((Relation) current.getOrCreate(VALUE, Type.RELATION)).clear().add(valueToInsert.id());
                                 current.set(SIZE, Type.INT, 1);
                                 return false; //stop the while loop and insert here
                             } else if (distance.measure(keyToInsert, nodeKey) < err) {
                                 ((Relation) current.getOrCreate(VALUE, Type.RELATION)).clear().add(valueToInsert.id());
                                 return false; //insert in the current node, and done with it, no need to continue looping
                             } else {
                                 //Decision point for next step
                                 Relation child;
                                 String nextRel;
                                 if (keyToInsert[lev] > nodeKey[lev]) {
                                     child = (Relation) current.get(RIGHT);
                                     nextRel = RIGHT;
                                 } else {
                                     child = (Relation) current.get(LEFT);
                                     nextRel = LEFT;
                                 }

                                 //If there is no node to the right, we create one and the game is over
                                 if (child == null || child.size() == 0) {
                                     KDTreeOld childNode = (KDTreeOld) ctx.graph().newTypedNode(current.world(), current.time(), NAME);
                                     childNode.set(KEY, Type.DOUBLE_ARRAY, keyToInsert);
                                     ((Relation) childNode.getOrCreate(VALUE, Type.RELATION)).clear().add(valueToInsert.id());
                                     ((Relation) current.getOrCreate(nextRel, Type.RELATION)).clear().add(childNode.id());
                                     root.set(SIZE, Type.INT, (Integer) root.get(SIZE) + 1);
                                     childNode.free();
                                     return false;
                                 } else {
                                     //Otherwise we need to prepare for the next while iteration
                                     ctx.setGlobalVariable("next", nextRel);
                                     ctx.setGlobalVariable("lev", (lev + 1) % dim);
                                     return true;
                                 }
                             }
                         }

                     },
                    newTask()
                            .then(traverse("{{next}}")));

    private static Task initFindNear() {
        Task reccursiveDown = newTask();
        reccursiveDown
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {

                        // 1. Load the variables and if kd is empty exit.

                        Node node = ctx.resultAsNodes().get(0);
                        if (node == null) {
                            ctx.continueTask();
                            return;
                        }


                        // node.graph().save(null);
                        // System.out.println("A- "+node.id()+": "+node.graph().space().available());

                        double[] pivot = (double[]) node.get(KEY);

                        //Global variable
                        int dim = (int) ctx.variable("dim").get(0);
                        double[] target = (double[]) ctx.variable("key").get(0);
                        Distance distance = (Distance) ctx.variable("distance").get(0);

                        //Local variables
                        int lev = (int) ctx.variable("lev").get(0);
                        HRect hr = (HRect) ctx.variable("hr").get(0);
                        double max_dist_sqd = (double) ctx.variable("max_dist_sqd").get(0);

                        // System.out.println("T1 " + node.id() + " lev " + lev);
                        //System.out.println("traversing " + node.id() + " lev: " + lev + ", key: " + pivot[0] + " , " + pivot[1] + " distance: " + distance.measure(pivot, target));

                        // 2. s := split field of kd
                        int s = lev % dim;
                        //System.out.println("T1 "+node.id()+ " lev "+lev+ " s "+s);

                        // 3. pivot := dom-elt field of kd

                        double pivot_to_target = distance.measure(pivot, target);

                        // 4. Cut hr into to sub-hyperrectangles left-hr and right-hr.
                        // The cut plane is through pivot and perpendicular to the s
                        // dimension.
                        HRect left_hr = hr; // optimize by not cloning
                        HRect right_hr = (HRect) hr.clone();
                        left_hr.max[s] = pivot[s];
                        right_hr.min[s] = pivot[s];

                        // 5. target-in-left := target_s <= pivot_s
                        boolean target_in_left = target[s] < pivot[s];

                        Relation nearer_kd;
                        HRect nearer_hr;
                        Relation further_kd;
                        HRect further_hr;
                        String nearer_st;
                        String farther_st;

                        // 6. if target-in-left then
                        // 6.1. nearer-kd := left field of kd and nearer-hr := left-hr
                        // 6.2. further-kd := right field of kd and further-hr := right-hr
                        if (target_in_left) {
                            nearer_kd = (Relation) node.get(LEFT);
                            nearer_st = LEFT;
                            nearer_hr = left_hr;

                            further_kd = (Relation) node.get(RIGHT);
                            further_hr = right_hr;
                            farther_st = RIGHT;
                        }
                        //
                        // 7. if not target-in-left then
                        // 7.1. nearer-kd := right field of kd and nearer-hr := right-hr
                        // 7.2. further-kd := left field of kd and further-hr := left-hr
                        else {
                            nearer_kd = (Relation) node.get(RIGHT);
                            nearer_hr = right_hr;
                            nearer_st = RIGHT;

                            further_kd = (Relation) node.get(LEFT);
                            further_hr = left_hr;
                            farther_st = LEFT;
                        }

                        //define contextual variables for reccursivity:
                        ctx.defineVariable("further_hr", further_hr);
                        ctx.defineVariable("pivot_to_target", pivot_to_target);

                        if (nearer_kd != null && nearer_kd.size() != 0) {
                            ctx.defineVariable("near", nearer_st);
                            //The 3 variables to set for next round of reccursivity:
                            ctx.defineVariableForSubTask("hr", nearer_hr);
                            ctx.defineVariableForSubTask("max_dist_sqd", max_dist_sqd);
                            ctx.defineVariableForSubTask("lev", lev + 1);

                        } else {
                            ctx.defineVariableForSubTask("near", ctx.newResult());  //stop the loop
                        }

                        if (further_kd != null && further_kd.size() != 0) {
                            ctx.defineVariableForSubTask("far", farther_st);
                        } else {
                            ctx.defineVariableForSubTask("far", ctx.newResult()); //stop the loop
                        }

                        ctx.continueTask();
                    }
                })
                .pipeTo(
                        newTask()
                                .ifThen(new ConditionalFunction() {
                                    @Override
                                    public boolean eval(TaskContext ctx) {
                                        return ctx.variable("near").size() > 0;
                                    }
                                }, newTask()
                                        .then(traverse("{{near}}"))
                                        .pipeTo(reccursiveDown)))

                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {

                        //Global variables
                        NearestNeighborList nnl = (NearestNeighborList) ctx.variable("nnl").get(0);
                        double[] target = (double[]) ctx.variable("key").get(0);
                        Distance distance = (Distance) ctx.variable("distance").get(0);

                        //Local variables
                        double max_dist_sqd = (double) ctx.variable("max_dist_sqd").get(0);
                        HRect further_hr = (HRect) ctx.variable("further_hr").get(0);
                        double pivot_to_target = (double) ctx.variable("pivot_to_target").get(0);
                        int lev = (int) ctx.variable("lev").get(0);
                        Node node = ctx.resultAsNodes().get(0);

                        // System.out.println("T2 " + node.id() + " lev " + lev);
                        //node.graph().save(null);
                        //  System.out.println("B- "+node.id()+": "+node.graph().space().available());

                        double dist_sqd;
                        if (!nnl.isCapacityReached()) {
                            dist_sqd = Double.MAX_VALUE;
                        } else {
                            dist_sqd = nnl.getMaxPriority();
                        }

                        // 9. max-dist-sqd := minimum of max-dist-sqd and dist-sqd
                        double max_dist_sqd2 = Math.min(max_dist_sqd, dist_sqd);

                        // 10. A nearer point could only lie in further-kd if there were some
                        // part of further-hr within distance sqrt(max-dist-sqd) of
                        // target. If this is the case then
                        double[] closest = further_hr.closest(target);
                        if (distance.measure(closest, target) < max_dist_sqd) {

                            // 10.1 if (pivot-target)^2 < dist-sqd then
                            if (pivot_to_target < dist_sqd) {

                                // 10.1.2 dist-sqd = (pivot-target)^2
                                dist_sqd = pivot_to_target;
                                //System.out.println("T3 "+node.id()+" insert-> "+((long[]) (node.get(INTERNAL_VALUE)))[0]);
                                //System.out.println("INSTASK " + ((long[]) (node.get(INTERNAL_VALUE)))[0] + " id: "+node.id());
                                nnl.insert(((Relation) (node.get(VALUE))).get(0), dist_sqd);
                                double[] pivot = (double[]) node.get(KEY);
                                //System.out.println("INSERT " + node.id() + " lev: " + lev + ", key: " + pivot[0] + " , " + pivot[1] + ", distance: " + distance.measure(pivot, target)+ ", value: "+ ((Relationship) (node.get(VALUE))).get(0)+ ", dist sqd: "+dist_sqd);

                                // 10.1.3 max-dist-sqd = dist-sqd
                                // max_dist_sqd = dist_sqd;
                                if (nnl.isCapacityReached()) {
                                    max_dist_sqd2 = nnl.getMaxPriority();
                                } else {
                                    max_dist_sqd2 = Double.MAX_VALUE;
                                }
                            }

                            // 10.2 Recursively call Nearest Neighbor with parameters
                            // (further-kd, target, further-hr, max-dist_sqd),
                            // storing results in temp-nearest and temp-dist-sqd
                            //nnbr(further_kd, target, further_hr, max_dist_sqd, lev + 1, K, nnl);


                            //The 3 variables to set for next round of reccursivity:
                            ctx.defineVariableForSubTask("hr", further_hr);
                            ctx.defineVariableForSubTask("max_dist_sqd", max_dist_sqd2);
                            ctx.defineVariableForSubTask("lev", lev + 1);


                            ctx.defineVariable("continueFar", true);

                        } else {
                            ctx.defineVariable("continueFar", false);
                        }
                        ctx.continueTask();
                    }
                })
                .pipeTo(
                        newTask()
                                .ifThen(new ConditionalFunction() {
                                            @Override
                                            public boolean eval(TaskContext ctx) {
                                                return ((boolean) ctx.variable("continueFar").get(0) && ctx.variable("far").size() > 0); //Exploring the far depends also on the distance
                                            }
                                        },
                                        newTask()
                                                .then(traverse("{{far}}"))
                                                .pipeTo(reccursiveDown)));


        return reccursiveDown;
    }

    private static Task initFindRadius() {
        Task reccursiveDown = newTask();
        reccursiveDown
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {

                        // 1. Load the variables and if kd is empty exit.

                        Node node = ctx.resultAsNodes().get(0);
                        if (node == null) {
                            ctx.continueTask();
                            return;
                        }

                        // node.graph().save(null);
                        // System.out.println("A- "+node.id()+": "+node.graph().space().available());

                        double[] pivot = (double[]) node.get(KEY);

                        //Global variable
                        int dim = (int) ctx.variable("dim").get(0);
                        double[] target = (double[]) ctx.variable("key").get(0);
                        Distance distance = (Distance) ctx.variable("distance").get(0);

                        //Local variables
                        int lev = (int) ctx.variable("lev").get(0);
                        HRect hr = (HRect) ctx.variable("hr").get(0);
                        double max_dist_sqd = (double) ctx.variable("max_dist_sqd").get(0);

                        // System.out.println("T1 " + node.id() + " lev " + lev);


                        // 2. s := split field of kd
                        int s = lev % dim;
                        //System.out.println("T1 "+node.id()+ " lev "+lev+ " s "+s);

                        // 3. pivot := dom-elt field of kd

                        double pivot_to_target = distance.measure(pivot, target);

                        // 4. Cut hr into to sub-hyperrectangles left-hr and right-hr.
                        // The cut plane is through pivot and perpendicular to the s
                        // dimension.
                        HRect left_hr = hr; // optimize by not cloning
                        HRect right_hr = (HRect) hr.clone();
                        left_hr.max[s] = pivot[s];
                        right_hr.min[s] = pivot[s];

                        // 5. target-in-left := target_s <= pivot_s
                        boolean target_in_left = target[s] < pivot[s];

                        Relation nearer_kd;
                        HRect nearer_hr;
                        Relation further_kd;
                        HRect further_hr;
                        String nearer_st;
                        String farther_st;

                        // 6. if target-in-left then
                        // 6.1. nearer-kd := left field of kd and nearer-hr := left-hr
                        // 6.2. further-kd := right field of kd and further-hr := right-hr
                        if (target_in_left) {
                            nearer_kd = (Relation) node.get(LEFT);
                            nearer_st = LEFT;
                            nearer_hr = left_hr;

                            further_kd = (Relation) node.get(RIGHT);
                            further_hr = right_hr;
                            farther_st = RIGHT;
                        }
                        //
                        // 7. if not target-in-left then
                        // 7.1. nearer-kd := right field of kd and nearer-hr := right-hr
                        // 7.2. further-kd := left field of kd and further-hr := left-hr
                        else {
                            nearer_kd = (Relation) node.get(RIGHT);
                            nearer_hr = right_hr;
                            nearer_st = RIGHT;

                            further_kd = (Relation) node.get(LEFT);
                            further_hr = left_hr;
                            farther_st = LEFT;
                        }

                        //define contextual variables for reccursivity:
                        ctx.defineVariable("further_hr", further_hr);
                        ctx.defineVariable("pivot_to_target", pivot_to_target);

                        if (nearer_kd != null && nearer_kd.size() != 0) {
                            ctx.defineVariable("near", nearer_st);
                            //The 3 variables to set for next round of reccursivity:
                            ctx.defineVariableForSubTask("hr", nearer_hr);
                            ctx.defineVariableForSubTask("max_dist_sqd", max_dist_sqd);
                            ctx.defineVariableForSubTask("lev", lev + 1);

                        } else {
                            ctx.defineVariableForSubTask("near", ctx.newResult());  //stop the loop
                        }

                        if (further_kd != null && further_kd.size() != 0) {
                            ctx.defineVariableForSubTask("far", farther_st);
                        } else {
                            ctx.defineVariableForSubTask("far", ctx.newResult()); //stop the loop
                        }

                        ctx.continueTask();
                    }
                })
                .pipeTo(
                        newTask()
                                .ifThen(new ConditionalFunction() {
                                    @Override
                                    public boolean eval(TaskContext ctx) {
                                        return ctx.variable("near").size() > 0;
                                    }
                                }, newTask()
                                        .then(traverse("{{near}}"))
                                        .pipeTo(reccursiveDown)))

                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {

                        //Global variables
                        NearestNeighborArrayList nnl = (NearestNeighborArrayList) ctx.variable("nnl").get(0);
                        double[] target = (double[]) ctx.variable("key").get(0);
                        Distance distance = (Distance) ctx.variable("distance").get(0);
                        double radius = (double) ctx.variable("radius").get(0);

                        //Local variables
                        double max_dist_sqd = (double) ctx.variable("max_dist_sqd").get(0);
                        HRect further_hr = (HRect) ctx.variable("further_hr").get(0);
                        double pivot_to_target = (double) ctx.variable("pivot_to_target").get(0);
                        int lev = (int) ctx.variable("lev").get(0);
                        Node node = ctx.resultAsNodes().get(0);
                        // System.out.println("T2 " + node.id() + " lev " + lev);

                        // node.graph().save(null);
                        //  System.out.println("B- "+node.id()+": "+node.graph().space().available());

                        double dist_sqd = Double.MAX_VALUE;


                        // 9. max-dist-sqd := minimum of max-dist-sqd and dist-sqd
                        double max_dist_sqd2 = Math.min(max_dist_sqd, dist_sqd);

                        // 10. A nearer point could only lie in further-kd if there were some
                        // part of further-hr within distance sqrt(max-dist-sqd) of
                        // target. If this is the case then
                        double[] closest = further_hr.closest(target);
                        if (distance.measure(closest, target) < max_dist_sqd) {

                            // 10.1 if (pivot-target)^2 < dist-sqd then
                            if (pivot_to_target < dist_sqd) {

                                // 10.1.2 dist-sqd = (pivot-target)^2
                                dist_sqd = pivot_to_target;
                                //System.out.println("T3 "+node.id()+" insert-> "+((long[]) (node.get(INTERNAL_VALUE)))[0]);
                                //System.out.println("INSTASK " + ((long[]) (node.get(INTERNAL_VALUE)))[0] + " id: "+node.id());
                                if (dist_sqd <= radius) {
                                    nnl.insert(((Relation) (node.get(VALUE))).get(0), dist_sqd);
                                }
                                // 10.1.3 max-dist-sqd = dist-sqd
                                // max_dist_sqd = dist_sqd;
                                max_dist_sqd2 = Double.MAX_VALUE;
                            }

                            // 10.2 Recursively call Nearest Neighbor with parameters
                            // (further-kd, target, further-hr, max-dist_sqd),
                            // storing results in temp-nearest and temp-dist-sqd
                            //nnbr(further_kd, target, further_hr, max_dist_sqd, lev + 1, K, nnl);

                            //The 3 variables to set for next round of reccursivity:
                            ctx.defineVariableForSubTask("hr", further_hr);
                            ctx.defineVariableForSubTask("max_dist_sqd", max_dist_sqd2);
                            ctx.defineVariableForSubTask("lev", lev + 1);

                            ctx.defineVariable("continueFar", true);

                        } else {
                            ctx.defineVariable("continueFar", false);
                        }
                        ctx.continueTask();
                    }
                })
                .pipeTo(
                        newTask()
                                .ifThen(new ConditionalFunction() {
                                    @Override
                                    public boolean eval(TaskContext ctx) {
                                        return ((boolean) ctx.variable("continueFar").get(0) && ctx.variable("far").size() > 0); //Exploring the far depends also on the distance
                                    }
                                }, newTask()
                                        .then(traverse("{{far}}"))
                                        .pipeTo(reccursiveDown)));
        return reccursiveDown;
    }

    //Static tasks to manage insert and find nearest neighbours
    //find nearest N neighbours task
    private static Task nearestTask = initFindNear();
    private static Task nearestRadiusTask = initFindRadius();

    private static final Enforcer enforcer = new Enforcer()
            .asPositiveDouble(DISTANCE_THRESHOLD);

    @Override
    public Node set(String propertyName, byte propertyType, Object propertyValue) {
        enforcer.check(propertyName, propertyType, propertyValue);
        return super.set(propertyName, propertyType, propertyValue);
    }

    public void insert(Node value, Callback<Boolean> callback) {
        extractFeatures(value, new Callback<double[]>() {
            @Override
            public void on(final double[] result) {
                insertWith(result, value, callback);
            }
        });
    }

    public void insertWith(final double[] key, final Node value, final Callback<Boolean> callback) {
        final NodeState state = unphasedState();
        final int dim = state.getFromKeyWithDefault(DIMENSIONS, key.length);
        final double err = state.getFromKeyWithDefault(DISTANCE_THRESHOLD, DISTANCE_THRESHOLD_DEF);
        if (key.length != dim) {
            throw new RuntimeException("Key size should always be the same");
        }
        if (value == null) {
            throw new RuntimeException("To index node should not be null");
        }
        Distance distance = getDistance(state);
        TaskContext tc = insert.prepare(graph(), this, new Callback<TaskResult>() {
            @Override
            public void on(TaskResult result) {
                result.free();
                if (callback != null) {
                    callback.on(true);
                }
            }
        });

        TaskResult res = tc.newResult();
        res.add(key);

        //Set global variables
        tc.setGlobalVariable("key", res);
        tc.setGlobalVariable("value", value);
        tc.setGlobalVariable("root", this);
        tc.setGlobalVariable("distance", distance);
        tc.setGlobalVariable("dim", dim);
        tc.setGlobalVariable("err", err);

        //Set local variables
        tc.defineVariable("lev", 0);
        insert.executeUsing(tc);
    }


    public int size() {
        return graph().resolver().resolveState(this).getFromKeyWithDefault(SIZE, 0);
    }


    public void setDistance(int distanceType) {
        set(DISTANCE, Type.INT, distanceType);
    }


    public void setDistanceThreshold(double distanceThreshold) {
        this.set(DISTANCE_THRESHOLD, Type.DOUBLE, distanceThreshold);
    }


    public void setFrom(String extractor) {
        set(FROM, Type.STRING, extractor);
    }


    public void nearestNWithinRadius(final double[] key, int n, double radius, final Callback<Node[]> callback) {
        NodeState state = unphasedState();
        final int dim = state.getFromKeyWithDefault(DIMENSIONS, key.length);

        if (key.length != dim) {
            throw new RuntimeException("Key size should always be the same");
        }

        // initial call is with infinite hyper-rectangle and max distance
        HRect hr = HRect.infiniteHRect(key.length);
        double max_dist_sqd = Double.MAX_VALUE;

        final NearestNeighborList nnl = new NearestNeighborList(n);
        Distance distance = getDistance(state);


        TaskContext tc = nearestTask.prepare(graph(), this, new Callback<TaskResult>() {
            @Override
            public void on(TaskResult result) {

                //ToDo replace by lookupAll later
                long[] res = nnl.getAllNodesWithin(radius);
                if (res.length != 0) {

                    Task lookupall =
                            newTask()
                                    .then(travelInWorld(String.valueOf(world())))
                                    .then(CoreActions.travelInTime(String.valueOf(time())))
                                    .then(readVar("res"))
                                    .then(lookupAll("{{result}}"));

                    TaskContext tc = lookupall.prepare(graph(), null, new Callback<TaskResult>() {
                        @Override
                        public void on(TaskResult result) {
                            final Node[] finalres = new Node[result.size()];
                            for (int i = 0; i < result.size(); i++) {
                                finalres[i] = (Node) result.get(i);
                            }
                            callback.on(finalres);
                        }
                    });

                    TaskResult tr = tc.wrap(res);
                    tc.addToGlobalVariable("res", tr);
                    lookupall.executeUsing(tc);
                } else {
                    callback.on(new Node[0]);
                }
            }
        });


        TaskResult res = tc.newResult().add(key);

        // (this, distance, key, hr, max_dist_sqd, 0, dim, err, nnl);

        tc.setGlobalVariable("key", res);
        tc.setGlobalVariable("distance", distance);
        tc.setGlobalVariable("dim", dim);
        tc.setGlobalVariable("nnl", nnl);

        tc.defineVariable("lev", 0);
        tc.defineVariable("hr", hr);
        tc.defineVariable("max_dist_sqd", max_dist_sqd);

        nearestTask.executeUsing(tc);

    }

    public void nearestWithinRadius(final double[] key, final double radius, final Callback<Node[]> callback) {
        NodeState state = unphasedState();
        final int dim = state.getFromKeyWithDefault(DIMENSIONS, key.length);

        if (key.length != dim) {
            throw new RuntimeException("Key size should always be the same");
        }

        // initial call is with infinite hyper-rectangle and max distance
        HRect hr = HRect.infiniteHRect(key.length);
        double max_dist_sqd = Double.MAX_VALUE;

        final NearestNeighborArrayList nnl = new NearestNeighborArrayList();
        Distance distance = getDistance(state);


        TaskContext tc = nearestRadiusTask.prepare(graph(), this, new Callback<TaskResult>() {
            @Override
            public void on(TaskResult result) {

                //ToDo replace by lookupAll later
                long[] res = nnl.distroyAndGetAllNodes();
                if (res.length != 0) {

                    Task lookupall = newTask().then(travelInWorld(String.valueOf(world()))).then(CoreActions.travelInTime(String.valueOf(time()))).then(readVar("res")).then(lookupAll("{{result}}"));
                    TaskContext tc = lookupall.prepare(graph(), null, new Callback<TaskResult>() {
                        @Override
                        public void on(TaskResult result) {
                            final Node[] finalres = new Node[result.size()];
                            for (int i = 0; i < result.size(); i++) {
                                finalres[i] = (Node) result.get(i);
                            }
                            callback.on(finalres);
                        }
                    });

                    TaskResult tr = tc.wrap(res);
                    tc.addToGlobalVariable("res", tr);
                    lookupall.executeUsing(tc);
                } else {
                    callback.on(new Node[0]);
                }
            }
        });


        TaskResult res = tc.newResult();
        res.add(key);

        // (this, distance, key, hr, max_dist_sqd, 0, dim, err, nnl);

        tc.setGlobalVariable("key", res);
        tc.setGlobalVariable("distance", distance);
        tc.setGlobalVariable("dim", dim);
        tc.setGlobalVariable("nnl", nnl);
        tc.setGlobalVariable("radius", radius);

        tc.defineVariable("lev", 0);
        tc.defineVariable("hr", hr);
        tc.defineVariable("max_dist_sqd", max_dist_sqd);

        nearestRadiusTask.executeUsing(tc);
    }


    public void nearestN(final double[] key, final int n, final Callback<Node[]> callback) {
        NodeState state = unphasedState();
        final int dim = state.getFromKeyWithDefault(DIMENSIONS, key.length);

        if (key.length != dim) {
            throw new RuntimeException("Key size should always be the same");
        }

        // initial call is with infinite hyper-rectangle and max distance
        HRect hr = HRect.infiniteHRect(key.length);
        double max_dist_sqd = Double.MAX_VALUE;

        final NearestNeighborList nnl = new NearestNeighborList(n);
        Distance distance = getDistance(state);


        TaskContext tc = nearestTask.prepare(graph(), this, new Callback<TaskResult>() {
            @Override
            public void on(TaskResult result) {

                //ToDo replace by lookupAll later
                long[] res = nnl.getNodes();

                if (res.length != 0) {
                    Task lookupall = newTask().then(travelInWorld(String.valueOf(world()))).then(CoreActions.travelInTime(String.valueOf(time()))).then(readVar("res")).then(lookupAll("{{result}}"));
                    TaskContext tc = lookupall.prepare(graph(), null, new Callback<TaskResult>() {
                        @Override
                        public void on(TaskResult result) {
                            final Node[] finalres = new Node[result.size()];
                            for (int i = 0; i < result.size(); i++) {
                                finalres[i] = (Node) result.get(i);
                            }
                            callback.on(finalres);
                        }
                    });

                    TaskResult tr = tc.wrap(res);
                    tc.addToGlobalVariable("res", tr);
                    lookupall.executeUsing(tc);
                } else {
                    callback.on(new Node[0]);
                }
            }
        });


        TaskResult res = tc.newResult();
        res.add(key);

        // (this, distance, key, hr, max_dist_sqd, 0, dim, err, nnl);

        tc.setGlobalVariable("key", res);
        tc.setGlobalVariable("distance", distance);
        tc.setGlobalVariable("dim", dim);
        tc.setGlobalVariable("nnl", nnl);

        tc.defineVariable("lev", 0);
        tc.defineVariable("hr", hr);
        tc.defineVariable("max_dist_sqd", max_dist_sqd);

        nearestTask.executeUsing(tc);
    }

    protected Distance getDistance(NodeState state) {
        int d = state.getFromKeyWithDefault(DISTANCE, DISTANCE_TYPE_DEF);
        Distance distance;
        if (d == Distances.EUCLIDEAN) {
            distance = EuclideanDistance.instance();
        } else if (d == Distances.GEODISTANCE) {
            distance = GeoDistance.instance();
        } else {
            throw new RuntimeException("Unknown distance code metric");
        }
        return distance;
    }

    protected void extractFeatures(final Node current, final Callback<double[]> callback) {
        String query = (String) super.get(FROM);
        if (query != null) {
            //TODO CACHE TO AVOID PARSING EVERY TIME
            String[] split = query.split(",");
            Task[] tasks = new Task[split.length];
            for (int i = 0; i < split.length; i++) {
                Task t = newTask().then(travelInWorld("" + current.world()));
                t.then(CoreActions.travelInTime(current.time() + ""));
                t.parse(split[i].trim(), graph());
                tasks[i] = t;
            }
            //END TODO IN CACHE
            final double[] result = new double[tasks.length];
            final DeferCounter waiter = graph().newCounter(tasks.length);
            for (int i = 0; i < split.length; i++) {
                //prepare initial result
                final TaskResult initial = emptyResult();
                initial.add(current);
                //prepare initial context
                final Callback<Integer> capsule = new Callback<Integer>() {
                    @Override
                    public void on(final Integer i) {
                        tasks[i].executeWith(graph(), initial, new Callback<TaskResult>() {
                            @Override
                            public void on(TaskResult currentResult) {
                                if (currentResult == null) {
                                    result[i] = Constants.NULL_LONG;
                                } else {
                                    if (currentResult.size() == 0) {
                                        //TODO LOG
                                        System.out.println("Feature extraction yield no result for query:" + tasks[i].toString() + " on node " + initial.toString());
                                    } else {
                                        result[i] = Double.parseDouble(currentResult.get(0).toString());
                                    }
                                    currentResult.free();
                                }
                                waiter.count();
                            }
                        });
                    }
                };
                capsule.on(i);
            }
            waiter.then(new Job() {
                @Override
                public void run() {
                    callback.on(result);
                }
            });
        } else {
            callback.on(null);
        }
    }


}