package com.example.manager;

import com.example.simulation.GameState;

import java.util.Random;

public class IdleBot extends Bot{
    @Override
    public String getName() {
        return "IdleBot";
    }

    @Override
    public int getMatrikel() {
        return 133769;
    }

    @Override
    public String getStudentName() {
        return "Santa";
    }

    private static Random random;

    @Override
    protected void init(GameState state) {
        long seed = 420L;
        random = new Random(seed);
    }

    @Override
    protected void executeTurn(GameState state, Controller controller) {

    }


}
