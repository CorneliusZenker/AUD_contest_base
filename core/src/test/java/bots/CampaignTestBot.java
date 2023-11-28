package bots;

import com.example.manager.StaticGameState;
import com.example.manager.player.Bot;
import com.example.manager.Controller;

public class CampaignTestBot extends Bot {
    @Override
    public String getStudentName() {
        return "Cornelius Zenker";
    }

    @Override
    public int getMatrikel() {
        return -1; //Heh, you thought
    }

    @Override
    public String getName() {
        return "Training Bot";
    }

    @Override
    public void init(StaticGameState state) {

    }

    @Override
    public void executeTurn(StaticGameState state, Controller controller) {
        controller.getRemainingUses();
    }
}
