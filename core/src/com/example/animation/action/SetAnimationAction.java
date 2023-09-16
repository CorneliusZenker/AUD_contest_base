package com.example.animation.action;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.example.animation.entity.AnimatedEntity;
import com.example.ui.assets.AssetContainer;

public class SetAnimationAction extends Action{
    private AnimatedEntity target;
    private Animation<TextureRegion> animation;

    public SetAnimationAction(float delay, AnimatedEntity target, Animation<TextureRegion> animation) {
        super(delay);
        this.target = target;
        this.animation = animation;
    }

    @Override
    protected void runAction(float oldTime, float current) {
        if(target!= null) target.setAnimation(animation);
        endAction(oldTime);
    }

    public void setTarget(AnimatedEntity target) {
        this.target = target;
    }
}
