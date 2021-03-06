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
package greycat.ml.neuralnet;

import greycat.Type;
import greycat.ml.common.matrix.VolatileDMatrix;
import greycat.struct.DMatrix;
import greycat.struct.ENode;

import java.util.Random;

/**
 * Created by assaad on 27/01/2017.
 */
public class ExMatrix implements DMatrix {

    private static String DW_KEY = "-dw";
    private static String STEPCACHE_KEY = "-sc";


    private DMatrix w;
    private DMatrix dw;
    private DMatrix stepCache;

    public ExMatrix(ENode node, String attribute) {
        if (node != null) {
            w = (DMatrix) node.getOrCreate(attribute, Type.DMATRIX);
            dw = (DMatrix) node.getOrCreate(attribute + DW_KEY, Type.DMATRIX);
            stepCache = (DMatrix) node.getOrCreate(attribute + STEPCACHE_KEY, Type.DMATRIX);
        }
    }


    public static ExMatrix empty(int rows, int column) {
        DMatrix dm = new ExMatrix(null, null);
        dm.init(rows, column);
        return (ExMatrix) dm;
    }

    @Override
    public DMatrix init(int rows, int columns) {
        if (w == null) {
            w = VolatileDMatrix.empty(rows, columns);
            dw = VolatileDMatrix.empty(rows, columns);
            stepCache = VolatileDMatrix.empty(rows, columns);
        } else {
            w.init(rows, columns);
            dw.init(rows, columns);
            stepCache.init(rows, columns);
        }
        return this;
    }

    @Override
    public DMatrix fill(double value) {
        w.fill(value);
        return this;
    }

    @Override
    public DMatrix fillWith(double[] values) {
        w.fillWith(values);
        return this;

    }

    @Override
    public DMatrix fillWithRandom(Random random, double min, double max) {
        w.fillWithRandom(random, min, max);
        return this;
    }

    @Override
    public DMatrix fillWithRandomStd(Random random, double std) {
        w.fillWithRandomStd(random, std);
        return this;
    }


    @Override
    public int rows() {
        return w.rows();
    }

    @Override
    public int columns() {
        return w.columns();
    }

    @Override
    public int length() {
        return w.length();
    }

    @Override
    public double[] column(int i) {
        return w.column(i);
    }

    @Override
    public double get(int rowIndex, int columnIndex) {
        return w.get(rowIndex, columnIndex);
    }

    @Override
    public DMatrix set(int rowIndex, int columnIndex, double value) {
        w.set(rowIndex, columnIndex, value);
        return this;
    }

    @Override
    public DMatrix add(int rowIndex, int columnIndex, double value) {
        w.add(rowIndex, columnIndex, value);
        return this;
    }

    @Override
    public DMatrix appendColumn(double[] newColumn) {
        w.appendColumn(newColumn);
        dw.appendColumn(new double[newColumn.length]);
        stepCache.appendColumn(new double[newColumn.length]);
        return null;
    }

    @Override
    public double[] data() {
        return w.data();
    }

    @Override
    public int leadingDimension() {
        return w.leadingDimension();
    }

    @Override
    public double unsafeGet(int index) {
        return w.unsafeGet(index);
    }

    @Override
    public DMatrix unsafeSet(int index, double value) {
        w.unsafeSet(index, value);
        return this;
    }

    public DMatrix getW() {
        return w;
    }

    public DMatrix getDw() {
        return dw;
    }

    public DMatrix getStepCache() {
        return stepCache;
    }


}
