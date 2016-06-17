package org.mwg.ml.algorithm.regression;

import org.mwg.Graph;
import org.mwg.Type;
import org.mwg.ml.RegressionNode;
import org.mwg.ml.common.AbstractRegressionSlidingWindowManagingNode;
import org.mwg.plugin.NodeState;

public abstract class AbstractLinearRegressionNode extends AbstractRegressionSlidingWindowManagingNode implements RegressionNode {

    /**
     * Regression coefficients
     */
    public static final String COEFFICIENTS_KEY = "regressionCoefficients";
    /**
     * Regression coefficients - default
     */
    public static final double[] COEFFICIENTS_DEF = new double[0];
    /**
     * Regression intercept
     */
    public static final String INTERCEPT_KEY = "regressionIntercept";
    /**
     * Regression intercept - default
     */
    public static final double INTERCEPT_DEF = 0.0;

    /**
     * L2 regularization coefficient
     */
    public static final String L2_COEF_KEY = "L2Coefficient";
    /**
     * L2 regularization coefficient - default
     */
    public static final double L2_COEF_DEF = 0.0;

    public AbstractLinearRegressionNode(long p_world, long p_time, long p_id, Graph p_graph, long[] currentResolution) {
        super(p_world, p_time, p_id, p_graph, currentResolution);
    }

    @Override
    public void setProperty(String propertyName, byte propertyType, Object propertyValue) {
        if (L2_COEF_KEY.equals(propertyName)) {
            illegalArgumentIfFalse( (propertyValue instanceof Double)||(propertyValue instanceof Integer),
                    "L2 regularization coefficient should be of type double or integer");
            if (propertyValue instanceof Double){
                illegalArgumentIfFalse((Double)propertyValue >= 0, "L2 regularization coefficient should be non-negative");
                setL2Regularization((Double)propertyValue);
            }else{
                illegalArgumentIfFalse((Integer)propertyValue >= 0, "L2 regularization coefficient should be non-negative");
                setL2Regularization((double)((Integer)propertyValue));
            }
        }else if (COEFFICIENTS_KEY.equals(propertyName) || INTERCEPT_KEY.equals(propertyName)) {
            //Nothing. Those cannot be set.
        }else{
            super.setProperty(propertyName, propertyType, propertyValue);
        }
    }

    @Override
    public Object get(String propertyName){
        if(COEFFICIENTS_KEY.equals(propertyName)){
            return unphasedState().getFromKeyWithDefault(COEFFICIENTS_KEY, COEFFICIENTS_DEF);
        }else if(INTERCEPT_KEY.equals(propertyName)){
            return unphasedState().getFromKeyWithDefault(COEFFICIENTS_KEY, COEFFICIENTS_DEF);
        }else if(L2_COEF_KEY.equals(propertyName)){
            return unphasedState().getFromKeyWithDefault(L2_COEF_KEY, L2_COEF_DEF);
        }
        return super.get(propertyName);
    }

    public double[] debugGetCoefficients() {
        // Objects.requireNonNull(coefficients,"Regression coefficients must be not null");
        return unphasedState().getFromKeyWithDefault(COEFFICIENTS_KEY, new double[0]);
    }

    public double debugGetL2Regularization() {
        // Objects.requireNonNull(coefficients,"Regression coefficients must be not null");
        return unphasedState().getFromKeyWithDefault(L2_COEF_KEY, 0.0);
    }

    public double debugGetIntercept() {
        // Objects.requireNonNull(coefficients,"Regression coefficients must be not null");
        return unphasedState().getFromKeyWithDefault(INTERCEPT_KEY, 0.0);
    }

    public void setL2Regularization(double l2) {
        illegalArgumentIfFalse(l2>=0,"L2 coefficients must be non-negative");
        unphasedState().setFromKey(L2_COEF_KEY, Type.DOUBLE, l2);
    }

    @Override
    protected void setBootstrapModeHook(NodeState state) {
        //What should we do when bootstrap mode is approaching?
        //TODO Nothing?
    }

    @Override
    public double predictValue(NodeState state, double curValue[]){
        return predictValueInternal(curValue, state.getFromKeyWithDefault(COEFFICIENTS_KEY, COEFFICIENTS_DEF), state.getFromKeyWithDefault(INTERCEPT_KEY, INTERCEPT_DEF));
    }

    private double predictValueInternal(double curValue[], double coefs[], double intercept){
        double response = 0;
        for (int i=0;i<curValue.length;i++){
            response += coefs[i]*curValue[i];
        }
        response += intercept;
        return response;
    }

    public double debugGetBufferError(){
        NodeState state = unphasedState();
        return getBufferError(state, (double[])state.getFromKey(INTERNAL_VALUE_BUFFER_KEY), (double[])state.getFromKey(INTERNAL_RESULTS_BUFFER_KEY));
    }

    @Override
    public double getBufferError(NodeState state, double valueBuffer[], double results[]) {
        //For each value in value buffer
        int startIndex = 0;
        final int dims = valueBuffer.length / results.length;

        final int numValues = valueBuffer.length / dims;//TODO What if there are not enough values?
        if (numValues == 0) {
            return 0;
        }

        double coefficients[] = state.getFromKeyWithDefault(COEFFICIENTS_KEY, COEFFICIENTS_DEF);
        int index = 0;
        double intercept = state.getFromKeyWithDefault(INTERCEPT_KEY, INTERCEPT_DEF);
        double sqrResidualSum = 0;
        while (startIndex + dims <= valueBuffer.length) { //For each value
            double curValue[] = new double[dims];
            System.arraycopy(valueBuffer, startIndex, curValue, 0, dims);
            double response = predictValueInternal(curValue,coefficients,intercept);

            sqrResidualSum += (response - results[index])*(response - results[index]);

            //Continue the loop
            startIndex += dims;
            index++;
        }
        return sqrResidualSum / numValues;
    }

    @Override
    public String toString(){
        StringBuilder result = new StringBuilder();
        double coefs[] = unphasedState().getFromKeyWithDefault(COEFFICIENTS_KEY, COEFFICIENTS_DEF);
        result.append("Coefficients: ");
        for (int j = 0; j < coefs.length; j++) {
            result.append(coefs[j] + ", ");
        }
        return result.toString();
    }
}
