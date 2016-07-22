package com.akalenda.MoogleKiwi.MatrixExecutable;

import java.util.ArrayList;
import java.util.concurrent.Executor;


public class MatrixExecutable {

    private Executor exec;
    private Runnable[][] matrix;

    private MatrixExecutable(int rows, int cols) {
        matrix = new Runnable[rows][cols];
    }

    public static MatrixExecutable ofDimensions(int rows, int cols) {
        return new MatrixExecutable(rows, cols);
    }

    public MatrixExecutable usingExecutor(Executor exec) {
        this.exec = exec;
        return this;
    }

    public ArrayList appliedTo(ArrayList vector) {
        return null; //TODO
    }
}
