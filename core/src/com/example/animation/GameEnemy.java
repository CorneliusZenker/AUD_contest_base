package com.example.animation;

import com.example.animation.entity.AnimatedEntity;
import com.example.animation.entity.Healthbar;
import com.example.ui.assets.AssetContainer;

import static com.example.ui.assets.AssetContainer.IngameAssets.gameCharacterAnimations;

public class GameEnemy extends AnimatedEntity {
    private int level;
    private Healthbar healthbar;

    public GameEnemy(int level) {
        super(gameCharacterAnimations[AssetContainer.IngameAssets.GameCharacterAnimationType.ANIMATION_TYPE_IDLE.ordinal()]);
        this.level = level;
        healthbar = new Healthbar(100);

    }
}
