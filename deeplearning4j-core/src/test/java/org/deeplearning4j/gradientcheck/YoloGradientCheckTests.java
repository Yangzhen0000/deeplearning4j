package org.deeplearning4j.gradientcheck;

import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.conf.layers.objdetect.Yolo2OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.junit.Test;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.util.DataTypeUtil;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.NoOp;

import static org.junit.Assert.*;

/**
 * @author Alex Black
 */
public class YoloGradientCheckTests {
    private static final boolean PRINT_RESULTS = true;
    private static final boolean RETURN_ON_FIRST_FAILURE = false;
    private static final double DEFAULT_EPS = 1e-6;
    private static final double DEFAULT_MAX_REL_ERROR = 1e-3;
    private static final double DEFAULT_MIN_ABS_ERROR = 1e-8;

    static {
        DataTypeUtil.setDTypeForContext(DataBuffer.Type.DOUBLE);
    }

    @Test
    public void testYoloOutputLayer() {
        int depthIn = 2;
        int[] minibatchSizes = {1, 3};
        int[] widths = new int[]{4, 7};
        int[] heights = new int[]{4, 5};
        int c = 3;
        int b = 3;

        int yoloDepth = 5*b + c;
        Activation a = Activation.TANH;

        Nd4j.getRandom().setSeed(12345);

        double[] l1 = new double[]{0.0, 0.3};
        double[] l2 = new double[]{0.0, 0.4};

        for( int wh = 0; wh<widths.length; wh++ ) {

            int w = widths[wh];
            int h = heights[wh];

            Nd4j.getRandom().setSeed(12345);
            INDArray bbPrior = Nd4j.rand(b, 2).muliRowVector(Nd4j.create(new double[]{w, h})).addi(0.1);

            for (int mb : minibatchSizes) {
                for (int i = 0; i < l1.length; i++) {

                    Nd4j.getRandom().setSeed(12345);

                    INDArray input = Nd4j.rand(new int[]{mb, depthIn, h, w});
                    INDArray labels = yoloLabels(mb, c, h, w);

                    MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(12345)
                            .updater(new NoOp())
                            .activation(a)
                            .l1(l1[i]).l2(l2[i])
                            .convolutionMode(ConvolutionMode.Same)
                            .list()
                            .layer(new ConvolutionLayer.Builder().kernelSize(2, 2).stride(1, 1)
                                    .nIn(depthIn).nOut(yoloDepth).build())//output: (5-2+0)/1+1 = 4
                            .layer(new Yolo2OutputLayer.Builder()
                                    .boundingBoxPriors(bbPrior)
                                    .build())
                            .build();

                    MultiLayerNetwork net = new MultiLayerNetwork(conf);
                    net.init();

                    String msg = "testYoloOutputLayer() - minibatch = " + mb + ", w=" + w + ", h=" + h + ", l1=" + l1[i] + ", l2=" + l2[i];
                    System.out.println(msg);

                    boolean gradOK = GradientCheckUtil.checkGradients(net, DEFAULT_EPS, DEFAULT_MAX_REL_ERROR,
                            DEFAULT_MIN_ABS_ERROR, PRINT_RESULTS, RETURN_ON_FIRST_FAILURE, input, labels);

                    assertTrue(msg, gradOK);
                }
            }
        }
    }

    private static INDArray yoloLabels(int mb, int c, int h, int w){
        int labelDepth = 4 + c;
        INDArray labels = Nd4j.zeros(mb, labelDepth, h, w);
        //put 1 object per minibatch, at positions (0,0), (1,1) etc.
        //Positions for label boxes: (1,1) to (2,2), (2,2) to (4,4) etc

        for( int i=0; i<mb; i++ ){
            //Class labels
            labels.putScalar(i, 4 + i%c, i%h, i%w, 1);

            //BB coordinates (top left, bottom right)
            labels.putScalar(i, 0, 0, 0, i%w);
            labels.putScalar(i, 1, 0, 0, i%h);
            labels.putScalar(i, 2, 0, 0, (i%w)+1);
            labels.putScalar(i, 3, 0, 0, (i%h)+1);
        }

        return labels;
    }
}
