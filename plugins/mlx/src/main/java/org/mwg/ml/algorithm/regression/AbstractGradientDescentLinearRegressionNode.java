package org.mwg.ml.algorithm.regression;

import org.mwg.Graph;
import org.mwg.Node;
import org.mwg.Type;
import org.mwg.ml.common.matrix.Matrix;
import org.mwg.ml.common.matrix.TransposeType;
import org.mwg.ml.common.matrix.operation.PInvSVD;
import org.mwg.plugin.NodeFactory;

/**
 * Created by andre on 4/29/2016.
 */
public abstract class AbstractGradientDescentLinearRegressionNode extends AbstractLinearRegressionNode{

    public static final String GD_ERROR_THRESH_KEY = "gdErrorThreshold";
    public static final String GD_ITERATION_THRESH_KEY = "gdIterationThreshold";

    public static final int DEFAULT_GD_ITERATIONS_COUNT = 10000;

    public static final double DEFAULT_LEARNING_RATE = 0.0001;

    /**
     * Attribute key - Learning rate
     */
    protected static final String INTERNAL_VALUE_LEARNING_RATE_KEY = "_LearningRate";

    public AbstractGradientDescentLinearRegressionNode(long p_world, long p_time, long p_id, Graph p_graph, long[] currentResolution) {
        super(p_world, p_time, p_id, p_graph, currentResolution);
    }

    public void setLearningRate(double newLearningRate){
        illegalArgumentIfFalse(newLearningRate > 0, "Learning rate should be positive");
        unphasedState().setFromKey(INTERNAL_VALUE_LEARNING_RATE_KEY, Type.DOUBLE, newLearningRate);
    }

    @Override
    public void setProperty(String propertyName, byte propertyType, Object propertyValue) {
        if (GD_ERROR_THRESH_KEY.equals(propertyName)){
            setIterationErrorThreshold((Double)propertyValue);
        }else if (GD_ITERATION_THRESH_KEY.equals(propertyName)){
            setIterationCountThreshold((Integer)propertyValue);
        }else if (INTERNAL_VALUE_LEARNING_RATE_KEY.equals(propertyName)){
            setLearningRate((Double)propertyValue);
        }else{
            super.setProperty(propertyName, propertyType, propertyValue);
        }
    }

    public double getIterationErrorThreshold() {
        return unphasedState().getFromKeyWithDefault(GD_ERROR_THRESH_KEY, Double.NaN);
    }

    public void setIterationErrorThreshold(double errorThreshold) {
        unphasedState().setFromKey(GD_ERROR_THRESH_KEY, Type.DOUBLE, errorThreshold);
    }

    public void removeIterationErrorThreshold() {
        unphasedState().setFromKey(GD_ERROR_THRESH_KEY, Type.DOUBLE, Double.NaN);
    }

    public int getIterationCountThreshold() {
        return unphasedState().getFromKeyWithDefault(GD_ITERATION_THRESH_KEY, DEFAULT_GD_ITERATIONS_COUNT);
    }

    public void setIterationCountThreshold(int iterationCountThreshold) {
        //Any value is acceptable.
        unphasedState().setFromKey(GD_ITERATION_THRESH_KEY, Type.INT, iterationCountThreshold);
    }

    public void removeIterationCountThreshold() {
        unphasedState().setFromKey(GD_ITERATION_THRESH_KEY, Type.INT, -1);
    }

}
