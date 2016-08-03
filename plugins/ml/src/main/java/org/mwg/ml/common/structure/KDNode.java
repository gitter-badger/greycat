package org.mwg.ml.common.structure;

import org.mwg.*;
import org.mwg.ml.common.distance.Distance;
import org.mwg.ml.common.distance.DistanceEnum;
import org.mwg.ml.common.distance.EuclideanDistance;
import org.mwg.ml.common.distance.GaussianDistance;
import org.mwg.plugin.*;
import org.mwg.task.*;

import static org.mwg.task.Actions.*;

/**
 * Created by assaad on 29/06/16.
 */
public class KDNode extends AbstractNode {

    public static final String NAME = "KDNode";

    private static final String INTERNAL_LEFT = "_left";                //to navigate left
    private static final String INTERNAL_RIGHT = "_right";              //to navigate right

    private static final String INTERNAL_KEY = "_key";                  //Values of the node
    private static final String INTERNAL_VALUE = "_value";              //Values of the node
    public static final String NUM_NODES = "_num";                      //Values of the node

    private static final String INTERNAL_DIM = "_dim";                  //Values of the node

    public static final String DISTANCE_THRESHOLD = "_threshold";       //Distance threshold
    public static final double DISTANCE_THRESHOLD_DEF = 1e-10;

    public static final String DISTANCE_TYPE = "disttype";
    public static final int DISTANCE_TYPE_DEF = 0;
    public static final String DISTANCE_PRECISION = "_precision";


    public KDNode(long p_world, long p_time, long p_id, Graph p_graph) {
        super(p_world, p_time, p_id, p_graph);
    }

    private static final Enforcer enforcer = new Enforcer()
            .asPositiveDouble(DISTANCE_THRESHOLD);

    @Override
    public void setProperty(String propertyName, byte propertyType, Object propertyValue) {
        enforcer.check(propertyName, propertyType, propertyValue);
        super.setProperty(propertyName, propertyType, propertyValue);
    }


    private Distance getDistance(NodeState state) {
        int d = state.getFromKeyWithDefault(DISTANCE_TYPE, DISTANCE_TYPE_DEF);
        Distance distance;
        if (d == DistanceEnum.EUCLIDEAN) {
            distance = new EuclideanDistance();
        } else if (d == DistanceEnum.GAUSSIAN) {
            double[] precision = (double[]) state.getFromKey(DISTANCE_PRECISION);
            if (precision == null) {
                throw new RuntimeException("covariance of gaussian distances cannot be null");
            }
            distance = new GaussianDistance(precision);
        } else {
            throw new RuntimeException("Unknown distance code metric");
        }
        return distance;
    }


    public void insert(final double[] key, final Node value, final Callback<Boolean> callback) {
        NodeState state = unphasedState();
        final int dim = state.getFromKeyWithDefault(INTERNAL_DIM, key.length);
        final double err = state.getFromKeyWithDefault(DISTANCE_THRESHOLD, DISTANCE_THRESHOLD_DEF);

        if (key.length != dim) {
            throw new RuntimeException("Key size should always be the same");
        }
        Distance distance = getDistance(state);
        internalInsert(this, this, distance, key, 0, dim, err, value, callback);
    }

    public void insertWithTask(final double[] key, final Node value, final Callback<Boolean> callback) {
        NodeState state = unphasedState();
        final int dim = state.getFromKeyWithDefault(INTERNAL_DIM, key.length);
        final double err = state.getFromKeyWithDefault(DISTANCE_THRESHOLD, DISTANCE_THRESHOLD_DEF);

        if (key.length != dim) {
            throw new RuntimeException("Key size should always be the same");
        }
        Distance distance = getDistance(state);
        internalInsertTask(this, this, distance, key, 0, dim, err, value, callback);
    }


    public void nearest(final double[] key, final Callback<Node> callback) {
        NodeState state = unphasedState();
        final int dim = state.getFromKeyWithDefault(INTERNAL_DIM, key.length);
        final double err = state.getFromKeyWithDefault(DISTANCE_THRESHOLD, DISTANCE_THRESHOLD_DEF);

        if (key.length != dim) {
            throw new RuntimeException("Key size should always be the same");
        }

        // initial call is with infinite hyper-rectangle and max distance
        HRect hr = HRect.infiniteHRect(key.length);
        double max_dist_sqd = Double.MAX_VALUE;

        NearestNeighborList nnl = new NearestNeighborList(1);
        Distance distance = getDistance(state);
        internalNearest(this, distance, key, hr, max_dist_sqd, 0, dim, err, nnl);

        long res = nnl.getHighest();
        graph().lookup(world(), time(), res, new Callback<Node>() {
            @Override
            public void on(Node result) {
                callback.on(result);
            }
        });
    }

    public void nearestWithinDistance(final double[] key, final Callback<Node> callback) {
        NodeState state = unphasedState();
        final int dim = state.getFromKeyWithDefault(INTERNAL_DIM, key.length);
        final double err = state.getFromKeyWithDefault(DISTANCE_THRESHOLD, DISTANCE_THRESHOLD_DEF);

        if (key.length != dim) {
            throw new RuntimeException("Key size should always be the same");
        }

        // initial call is with infinite hyper-rectangle and max distance
        HRect hr = HRect.infiniteHRect(key.length);
        double max_dist_sqd = Double.MAX_VALUE;

        NearestNeighborList nnl = new NearestNeighborList(1);
        Distance distance = getDistance(state);
        internalNearest(this, distance, key, hr, max_dist_sqd, 0, dim, err, nnl);

        if (nnl.getBestDistance() <= err) {
            long res = nnl.getHighest();

            graph().lookup(world(), time(), res, new Callback<Node>() {
                @Override
                public void on(Node result) {
                    callback.on(result);
                }
            });
        } else {
            callback.on(null);
        }
    }


    public void nearestNTask(final double[] key, final int n, final Callback<Node[]> callback) {
        NodeState state = unphasedState();
        final int dim = state.getFromKeyWithDefault(INTERNAL_DIM, key.length);

        if (key.length != dim) {
            throw new RuntimeException("Key size should always be the same");
        }

        // initial call is with infinite hyper-rectangle and max distance
        HRect hr = HRect.infiniteHRect(key.length);
        double max_dist_sqd = Double.MAX_VALUE;

        final NearestNeighborList nnl = new NearestNeighborList(n);
        Distance distance = getDistance(state);


        TaskContext tc = nearestTask.prepareWith(graph(), this, new Callback<TaskResult>() {
            @Override
            public void on(TaskResult result) {

                //ToDo replace by lookup parallel task
                long[] res = nnl.getAllNodes();
                DeferCounter counter = graph().newCounter(res.length);
                final Node[] finalres = new Node[res.length];
                for (int i = 0; i < res.length; i++) {
                    int finalI = i;
                    graph().lookup(world(), time(), res[i], new Callback<Node>() {
                        @Override
                        public void on(Node result) {
                            finalres[finalI] = result;
                            counter.count();
                        }
                    });
                }
                counter.then(new Job() {
                    @Override
                    public void run() {
                        callback.on(finalres);
                    }
                });
                result.free();
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


    public void nearestN(final double[] key, final int n, final Callback<Node[]> callback) {
        NodeState state = unphasedState();
        final int dim = state.getFromKeyWithDefault(INTERNAL_DIM, key.length);
        final double err = state.getFromKeyWithDefault(DISTANCE_THRESHOLD, DISTANCE_THRESHOLD_DEF);

        if (key.length != dim) {
            throw new RuntimeException("Key size should always be the same");
        }

        // initial call is with infinite hyper-rectangle and max distance
        HRect hr = HRect.infiniteHRect(key.length);
        double max_dist_sqd = Double.MAX_VALUE;

        NearestNeighborList nnl = new NearestNeighborList(n);
        Distance distance = getDistance(state);
        internalNearest(this, distance, key, hr, max_dist_sqd, 0, dim, err, nnl);

        long[] res = nnl.getAllNodes();
        DeferCounter counter = graph().newCounter(res.length);
        final Node[] finalres = new Node[res.length];
        for (int i = 0; i < res.length; i++) {
            int finalI = i;
            graph().lookup(world(), time(), res[i], new Callback<Node>() {
                @Override
                public void on(Node result) {
                    finalres[finalI] = result;
                    counter.count();
                }
            });
        }
        counter.then(new Job() {
            @Override
            public void run() {
                callback.on(finalres);
            }
        });
    }


    //todo make it async with callback
    protected KDNode getNode(NodeState state, String key) {
        long[] ids = (long[]) state.getFromKey(key);
        if (ids != null) {
            KDNode[] res = new KDNode[1];
            DeferCounterSync counter = graph().newSyncCounter(1);
            graph().lookup(world(), time(), ids[0], new Callback<Node>() {
                @Override
                public void on(Node result) {
                    res[0] = (KDNode) result;
                    counter.count();
                }
            });
            counter.waitResult();
            return res[0];


        }
        return null;
    }


    private static Task insert = whileDo(new TaskFunctionConditional() {
        @Override
        public boolean eval(TaskContext context) {

            Node current = context.resultAsNodes().get(0);
            double[] nodeKey = (double[]) current.get(INTERNAL_KEY);

            //Get variables from context

            //toDo optimize the variables here
            int dim = (int) context.variable("dim").get(0);
            double[] keyToInsert = (double[]) context.variable("key").get(0);

            Node valueToInsert = (Node) context.variable("value").get(0);
            Node root = (Node) context.variable("root").get(0);
            Distance distance = (Distance) context.variable("distance").get(0);
            double err = (double) context.variable("err").get(0);
            int lev = (int) context.variable("lev").get(0);


            //Bootstrap, first insert ever
            if (nodeKey == null) {
                current.setProperty(INTERNAL_KEY, Type.DOUBLE_ARRAY, keyToInsert);
                current.setProperty(INTERNAL_VALUE, Type.RELATION, new long[]{valueToInsert.id()});
                current.setProperty(NUM_NODES, Type.INT, 1);
                return false; //stop the while loop and insert here
            } else if (distance.measure(keyToInsert, nodeKey) < err) {
                current.setProperty(INTERNAL_VALUE, Type.RELATION, new long[]{valueToInsert.id()});
                return false; //insert in the current node, and done with it, no need to continue looping
            } else {
                //Decision point for next step
                long[] child = null;
                String nextRel;
                if (keyToInsert[lev] > nodeKey[lev]) {
                    child = (long[]) current.get(INTERNAL_RIGHT);
                    nextRel = INTERNAL_RIGHT;
                } else {
                    child = (long[]) current.get(INTERNAL_LEFT);
                    nextRel = INTERNAL_LEFT;
                }

                //If there is no node to the right, we create one and the game is over
                if (child == null || child.length == 0) {
                    KDNode childNode = (KDNode) context.graph().newTypedNode(current.world(), current.time(), NAME);
                    childNode.setProperty(INTERNAL_KEY, Type.DOUBLE_ARRAY, keyToInsert);
                    childNode.setProperty(INTERNAL_VALUE, Type.RELATION, new long[]{valueToInsert.id()});
                    current.setProperty(nextRel, Type.RELATION, new long[]{childNode.id()});
                    root.setProperty(NUM_NODES, Type.INT, (Integer) root.get(NUM_NODES) + 1);
                    childNode.free();
                    return false;
                } else {
                    //Otherwise we need to prepare for the next while iteration
                    context.setGlobalVariable("next", nextRel);
                    context.setGlobalVariable("lev", (lev + 1) % dim);
                    return true;
                }
            }
        }

    }, traverse("{{next}}"));


    private static Task initFindNear() {
        Task reccursiveDown = newTask();

        reccursiveDown.then(new Action() {
            @Override
            public void eval(TaskContext context) {

                // 1. Load the variables and if kd is empty exit.

                Node node = context.resultAsNodes().get(0);
                if (node == null) {
                    context.continueTask();
                    return;
                }

                //  printerTask.println(node.id());
                // printerTask.flush();
                //print(node,"Task ");

                double[] pivot = (double[]) node.get(INTERNAL_KEY);

                //Global variable
                int dim = (int) context.variable("dim").get(0);
                double[] target = (double[]) context.variable("key").get(0);
                Distance distance = (Distance) context.variable("distance").get(0);

                //Local variables
                int lev = (int) context.variable("lev").get(0);
                HRect hr = (HRect) context.variable("hr").get(0);
                double max_dist_sqd = (double) context.variable("max_dist_sqd").get(0);

                if (context.variable("hrN") != null) {
                    lev = (int) context.variable("levN").get(0);
                    hr = (HRect) context.variable("hrN").get(0);
                    max_dist_sqd = (double) context.variable("max_dist_sqdN").get(0);
                    context.defineVariable("hr", hr);
                    context.defineVariable("max_dist_sqd", max_dist_sqd);
                    context.defineVariable("lev", lev);
                }


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

                long[] nearer_kd;
                HRect nearer_hr;
                long[] further_kd;
                HRect further_hr;
                String nearer_st;
                String farther_st;

                // 6. if target-in-left then
                // 6.1. nearer-kd := left field of kd and nearer-hr := left-hr
                // 6.2. further-kd := right field of kd and further-hr := right-hr
                if (target_in_left) {
                    nearer_kd = (long[]) node.get(INTERNAL_LEFT);
                    nearer_st = INTERNAL_LEFT;
                    nearer_hr = left_hr;

                    further_kd = (long[]) node.get(INTERNAL_RIGHT);
                    further_hr = right_hr;
                    farther_st = INTERNAL_RIGHT;
                }
                //
                // 7. if not target-in-left then
                // 7.1. nearer-kd := right field of kd and nearer-hr := right-hr
                // 7.2. further-kd := left field of kd and further-hr := left-hr
                else {
                    nearer_kd = (long[]) node.get(INTERNAL_RIGHT);
                    nearer_hr = right_hr;
                    nearer_st = INTERNAL_RIGHT;


                    further_kd = (long[]) node.get(INTERNAL_LEFT);
                    further_hr = left_hr;
                    farther_st = INTERNAL_LEFT;
                }

                //define contextual variables for reccursivity:

                if (nearer_kd != null && nearer_kd.length != 0) {
                    context.defineVariable("near", nearer_st);

                } else {
                    context.defineVariable("near", context.newResult());  //stop the loop
                }

                if (further_kd != null && further_kd.length != 0) {
                    context.defineVariable("far", farther_st);
                } else {
                    context.defineVariable("far", context.newResult()); //stop the loop
                }

                context.defineVariable("further_hr", further_hr);
                context.defineVariable("pivot_to_target", pivot_to_target);


                //The 3 variables to set for next round of reccursivity:
//                context.defineVariableWith("hr", nearer_hr);
//                context.defineVariableWith("max_dist_sqd", max_dist_sqd);
//                context.defineVariableWith("lev", lev + 1);


                context.defineVariable("hrN", nearer_hr);
                context.defineVariable("max_dist_sqdN", max_dist_sqd);
                context.defineVariable("levN", lev + 1);

                context.continueTask();


            }
        }).subTasks(new Task[]{

                ifThen(new TaskFunctionConditional() {
                    @Override
                    public boolean eval(TaskContext context) {
                        return context.variable("near").size() > 0;
                    }
                }, traverse("{{near}}").subTask(reccursiveDown))


                ,

                then(new Action() {
                    @Override
                    public void eval(TaskContext context) {


                        //Global variables
                        NearestNeighborList nnl = (NearestNeighborList) context.variable("nnl").get(0);
                        double[] target = (double[]) context.variable("key").get(0);
                        Distance distance = (Distance) context.variable("distance").get(0);


                        //Local variables
                        double max_dist_sqd = (double) context.variable("max_dist_sqd").get(0);
                        HRect further_hr = (HRect) context.variable("further_hr").get(0);
                        double pivot_to_target = (double) context.variable("pivot_to_target").get(0);
                        int lev = (int) context.variable("lev").get(0);
                        Node node = context.resultAsNodes().get(0);
                        //System.out.println("T2 "+node.id()+ " lev "+lev);


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
                                nnl.insert(((long[]) (node.get(INTERNAL_VALUE)))[0], dist_sqd);

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
//                            context.defineVariableWith("hr", further_hr);
//                            context.defineVariableWith("max_dist_sqd", max_dist_sqd2);
//                            context.defineVariableWith("lev", lev + 1);

                            context.defineVariable("hrN", further_hr);
                            context.defineVariable("max_dist_sqdN", max_dist_sqd2);
                            context.defineVariable("levN", lev + 1);


                            context.defineVariable("continueFar", true);

                        } else {
                            context.defineVariable("continueFar", false);
                        }
                        context.continueTask();
                    }
                }).ifThen(new TaskFunctionConditional() {
                    @Override
                    public boolean eval(TaskContext context) {
                        return ((boolean) context.variable("continueFar").get(0) && context.variable("far").size() > 0); //Exploring the far depends also on the distance
                    }
                }, traverse("{{far}}").subTask(reccursiveDown))
        });


        return reccursiveDown;
    }

    private static Task nearestTask = initFindNear();


    private static void internalNearest(KDNode node, final Distance distance, final double[] target, final HRect hr, final double max_dist_sqd, final int lev, final int dim, final double err, final NearestNeighborList nnl) {
        // 1. if kd is empty exit.

        if (node == null) {
            return;
        }

        NodeState state = node.unphasedState();
        double[] pivot = (double[]) state.getFromKey(INTERNAL_KEY);
        if (pivot == null) {
            return;
        }

        // 2. s := split field of kd
        int s = lev % dim;
        //System.out.println("S1 "+node.id()+ " lev "+lev+ " s "+s);

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

        KDNode nearer_kd;
        HRect nearer_hr;
        KDNode further_kd;
        HRect further_hr;

        // 6. if target-in-left then
        // 6.1. nearer-kd := left field of kd and nearer-hr := left-hr
        // 6.2. further-kd := right field of kd and further-hr := right-hr
        if (target_in_left) {
            nearer_kd = node.getNode(state, INTERNAL_LEFT);
            nearer_hr = left_hr;
            further_kd = node.getNode(state, INTERNAL_RIGHT);
            further_hr = right_hr;
        }
        //
        // 7. if not target-in-left then
        // 7.1. nearer-kd := right field of kd and nearer-hr := right-hr
        // 7.2. further-kd := left field of kd and further-hr := left-hr
        else {
            nearer_kd = node.getNode(state, INTERNAL_RIGHT);
            nearer_hr = right_hr;
            further_kd = node.getNode(state, INTERNAL_LEFT);
            further_hr = left_hr;
        }

        // 8. Recursively call Nearest Neighbor with paramters
        // (nearer-kd, target, nearer-hr, max-dist-sqd), storing the
        // results in nearest and dist-sqd
        //nnbr(nearer_kd, target, nearer_hr, max_dist_sqd, lev + 1, K, nnl);

        double finalMax_dist_sqd = max_dist_sqd;
        node.graph().scheduler().dispatch(SchedulerAffinity.SAME_THREAD, new Job() {
            @Override
            public void run() {
                internalNearest(nearer_kd, distance, target, nearer_hr, finalMax_dist_sqd, lev + 1, dim, err, nnl);
                if (nearer_kd != null) {
                    nearer_kd.free();
                }
            }
        });

        // System.out.println("S2 "+node.id()+ " lev "+ lev);


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
                //     System.out.println("S3 "+node.id()+" -> insert "+((long[]) state.getFromKey(INTERNAL_VALUE))[0]);
                nnl.insert(((long[]) state.getFromKey(INTERNAL_VALUE))[0], dist_sqd);

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

            double finalMax_dist_sqd1 = max_dist_sqd2;
            node.graph().scheduler().dispatch(SchedulerAffinity.SAME_THREAD, new Job() {
                @Override
                public void run() {
                    internalNearest(further_kd, distance, target, further_hr, finalMax_dist_sqd1, lev + 1, dim, err, nnl);

                    if (further_kd != null) {
                        further_kd.free();
                    }
                }
            });
        } else {
            if (further_kd != null) {
                further_kd.free();
            }
        }

    }


    private static void internalInsertTask(final KDNode node, final KDNode root, final Distance distance, final double[] keyToInsert, final int lev, final int dim, final double err, final Node valueToInsert, final Callback<Boolean> callback) {


        TaskContext tc = insert.prepareWith(node.graph(), root, new Callback<TaskResult>() {
            @Override
            public void on(TaskResult result) {
                result.free();
                if (callback != null) {
                    callback.on(true);
                }
            }
        });


        TaskResult res = tc.newResult();
        res.add(keyToInsert);

        tc.setGlobalVariable("key", res);
        tc.setGlobalVariable("value", valueToInsert);
        tc.setGlobalVariable("root", root);
        tc.setGlobalVariable("distance", distance);
        tc.setGlobalVariable("err", err);
        tc.setGlobalVariable("lev", lev);
        tc.setGlobalVariable("dim", dim);

        insert.executeUsing(tc);

    }


    private static void internalInsert(final KDNode node, final KDNode root, final Distance distance, final double[] keyToInsert, final int lev, final int dim, final double err, final Node valueToInsert, final Callback<Boolean> callback) {
        NodeState state = node.unphasedState();
        double[] nodeKey = (double[]) state.getFromKey(INTERNAL_KEY);
        if (nodeKey == null) {
            state.setFromKey(INTERNAL_KEY, Type.DOUBLE_ARRAY, keyToInsert);
            state.setFromKey(INTERNAL_VALUE, Type.RELATION, new long[]{valueToInsert.id()});
            if (node == root) {
                state.setFromKey(NUM_NODES, Type.INT, 1);
            }
            if (node != root) {
                node.free();
            }
            if (callback != null) {
                callback.on(true);
            }
            return;

        } else if (distance.measure(keyToInsert, nodeKey) < err) {
            state.setFromKey(INTERNAL_VALUE, Type.RELATION, new long[]{valueToInsert.id()});
            if (node != root) {
                node.free();
            }
            if (callback != null) {
                callback.on(true);
            }
            return;
        } else {
            long[] child = null;
            String nextRel;
            if (keyToInsert[lev] > nodeKey[lev]) {
                child = (long[]) state.getFromKey(INTERNAL_RIGHT);
                nextRel = INTERNAL_RIGHT;
            } else {
                child = (long[]) state.getFromKey(INTERNAL_LEFT);
                nextRel = INTERNAL_LEFT;
            }

            if (child == null || child.length == 0) {
                KDNode childNode = (KDNode) root.graph().newTypedNode(root.world(), root.time(), NAME);
                childNode.set(INTERNAL_KEY, keyToInsert);
                childNode.add(INTERNAL_VALUE, valueToInsert);
                state.setFromKey(nextRel, Type.RELATION, new long[]{childNode.id()});
                root.set(NUM_NODES, (Integer) root.get(NUM_NODES) + 1);
                childNode.free();
                if (node != root) {
                    node.free();
                }
                if (callback != null) {
                    callback.on(true);
                }
                return;
            } else {
                node.rel(nextRel, new Callback<Node[]>() {
                    @Override
                    public void on(Node[] result) {
                        if (node != root) {
                            node.free();
                        }
                        if (result == null || result.length == 0 || result[0] == null) {
                            System.out.println("RES NULL !!! " + result.length);
                        } else {
                            root.graph().scheduler().dispatch(SchedulerAffinity.SAME_THREAD, new Job() {
                                @Override
                                public void run() {
                                    internalInsert((KDNode) result[0], root, distance, keyToInsert, (lev + 1) % dim, dim, err, valueToInsert, callback);
                                }
                            });
                        }
                    }
                });
                return;
            }
        }
    }


}
