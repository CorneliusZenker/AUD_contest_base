package com.gatdsen.animation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FillViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.gatdsen.animation.action.Action;
import com.gatdsen.animation.action.DestroyAction;
import com.gatdsen.animation.action.IdleAction;
import com.gatdsen.animation.action.SummonAction;
import com.gatdsen.animation.action.uiActions.MessageUiGameEndedAction;
import com.gatdsen.animation.action.uiActions.MessageUiScoreAction;
import com.gatdsen.animation.entity.Entity;
import com.gatdsen.animation.entity.SpriteEntity;
import com.gatdsen.animation.entity.TileMap;
import com.gatdsen.manager.AnimationLogProcessor;
import com.gatdsen.simulation.GameState;
import com.gatdsen.simulation.IntVector2;
import com.gatdsen.simulation.action.*;
import com.gatdsen.ui.assets.AssetContainer.IngameAssets;
import com.gatdsen.ui.hud.UiMessenger;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Kernklasse für die Visualisierung des Spielgeschehens.
 * Übersetzt {@link GameState GameState} und {@link ActionLog ActionLog}
 * des {@link com.gatdsen.simulation Simulation-Package} in für libGDX renderbare Objekte
 */
public class Animator implements Screen, AnimationLogProcessor {
    private AnimatorCamera camera;
    private GameState state;
    private final UiMessenger uiMessenger;

    private Viewport viewport;
    private Viewport backgroundViewport;

    private SpriteEntity background;


    private final Batch batch;

    private final Entity root;

    private TileMap mapPlayer0;
    private TileMap mapPlayer1;

    private static GameEnemy[][][] enemies;

    // TODO: BlockingQueue<ActionLog> muss BlockingQueue<Action> sein - gez. Corny
    private final BlockingQueue<ActionLog> pendingLogs = new LinkedBlockingQueue<>();


    private final Object notificationObject = new Object();

    private final List<Action> actionList = new LinkedList<>();


    public AnimatorCamera getCamera() {
        return this.camera;
    }


    interface ActionConverter {
        public ExpandedAction apply(com.gatdsen.simulation.action.Action simAction, Animator animator);
    }

    private static class Remainder {
        private float remainingDelta;
        private Action[] actions;

        public Remainder(float remainingDelta, Action[] actions) {
            this.remainingDelta = remainingDelta;
            this.actions = actions;
        }

        public float getRemainingDelta() {
            return remainingDelta;
        }

        public Action[] getActions() {
            return actions;
        }
    }

    /**
     * One Simulation Action may be sliced into multiple Animation Actions to keep generalization high
     */
    private static class ExpandedAction {
        Action head;
        Action tail;

        public ExpandedAction(Action head) {
            this.head = head;
            this.tail = head;
        }

        public ExpandedAction(Action head, Action tail) {
            this.head = head;
            this.tail = tail;
        }
    }

    public static class ActionConverters {

        private static final Map<Class<?>, ActionConverter> map =
                new HashMap<Class<?>, ActionConverter>() {
                    {
                        put(InitAction.class, ((simAction, animator) -> new ExpandedAction(new IdleAction(0, 0))));
                        put(TurnStartAction.class, ActionConverters::convertTurnStartAction);
                        put(GameOverAction.class, ActionConverters::convertGameOverAction);
                        put(DebugPointAction.class, ActionConverters::convertDebugPointAction);
                        put(ScoreAction.class, ActionConverters::convertScoreAction);
                        put(EnemySpawnAction.class, ActionConverters::convertEnemySpawnAction);
                        put(TowerPlaceAction.class, ActionConverters::convertTowerPlaceAction);
                    }
                };


        public static Action convert(com.gatdsen.simulation.action.Action simAction, Animator animator) {
//            System.out.println("Converting " + simAction.getClass());
            ExpandedAction expandedAction = map.getOrDefault(simAction.getClass(), (v, w) -> {
                        System.err.println("Missing Converter for Action of type " + simAction.getClass());
                        return new ExpandedAction(new IdleAction(simAction.getDelay(), 0));
                    })
                    .apply(simAction, animator);
            expandedAction.tail.setChildren(extractChildren(simAction, animator));

            return expandedAction.head;
        }

        private static Action[] extractChildren(com.gatdsen.simulation.action.Action action, Animator animator) {
            int childCount = action.getChildren().size();
            if (childCount == 0) return new Action[]{};

            Action[] children = new Action[childCount];
            int i = 0;
            Iterator<com.gatdsen.simulation.action.Action> iterator = action.iterator();
            while (iterator.hasNext()) {
                com.gatdsen.simulation.action.Action curChild = iterator.next();
                children[i] = convert(curChild, animator);
                i++;
            }

            return children;
        }


        private static ExpandedAction convertEnemySpawnAction(com.gatdsen.simulation.action.Action action, Animator animator) {
            EnemySpawnAction spawnAction = (EnemySpawnAction) action;

            SummonAction<GameEnemy> spawnEnemy = new SummonAction<>(
                    spawnAction.getDelay(),
                    (GameEnemy enemy) -> {
                        enemies[spawnAction.getTeam()][spawnAction.getPosition().x][spawnAction.getPosition().y] = enemy;
                    },
                    () -> new GameEnemy(spawnAction.getLevel())
            );

            return new ExpandedAction(spawnEnemy);
        }


        private static ExpandedAction convertEnemyDefeatAction(com.gatdsen.simulation.action.Action action, Animator animator) {
            EnemyDefeatAction defeatAction = (EnemyDefeatAction) action;

            DestroyAction<GameEnemy> killEnemy = new DestroyAction<>(
                    defeatAction.getDelay(),
                    enemies[defeatAction.getTeam()][defeatAction.getPosition().x][defeatAction.getPosition().y],
                    null,
                    (GameEnemy enemy) -> enemies[defeatAction.getTeam()][defeatAction.getPosition().x][defeatAction.getPosition().y] = null
            );

            return new ExpandedAction(killEnemy);
        }

        private static ExpandedAction convertTowerPlaceAction(com.gatdsen.simulation.action.Action action, Animator animator) {
            TowerPlaceAction placeAction = (TowerPlaceAction) action;

            SummonAction summonTower = new SummonAction(action.getDelay(), target -> {
                // ToDo: change sim TowerPlaceAction to include a team
                //towers[placeAction.getTeam()][placeAction.getPos().x][placeAction.getPos().y] = target;
            }, () -> {
                Entity tower = new GameTower(1, placeAction.getType());
                animator.root.add(tower);

                return tower;
            });

            return new ExpandedAction(summonTower);
        }

        private static ExpandedAction convertTurnStartAction(com.gatdsen.simulation.action.Action action, Animator animator) {
            TurnStartAction startAction = (TurnStartAction) action;

            //ToDo: make necessary changes on Turnstart

            //ui Action

            return new ExpandedAction(new IdleAction(0,0));
        }


        private static ExpandedAction convertGameOverAction(com.gatdsen.simulation.action.Action action, Animator animator) {
            GameOverAction winAction = (GameOverAction) action;
            MessageUiGameEndedAction gameEndedAction;
            if (winAction.getTeam() < 0) {
                gameEndedAction = new MessageUiGameEndedAction(0, animator.uiMessenger, true);
            } else {
                gameEndedAction = new MessageUiGameEndedAction(0, animator.uiMessenger, true, winAction.getTeam(), Color.CYAN);
            }

            return new ExpandedAction(gameEndedAction);
        }

        private static ExpandedAction convertDebugPointAction(com.gatdsen.simulation.action.Action action, Animator animator) {
            DebugPointAction debugPointAction = (DebugPointAction) action;

            DestroyAction<Entity> destroyAction = new DestroyAction<Entity>(debugPointAction.getDuration(), null, null, animator.root::remove);

            SummonAction<Entity> summonAction = new SummonAction<Entity>(action.getDelay(), destroyAction::setTarget, () -> {
                SpriteEntity entity;
                if (debugPointAction.isCross()) {
                    entity = new SpriteEntity(IngameAssets.cross_marker);
                    entity.setSize(new Vector2(3, 3));
                    debugPointAction.getPos().sub(new IntVector2(1, 1));
                } else {
                    entity = new SpriteEntity(IngameAssets.pixel);
                }
                entity.setRelPos(debugPointAction.getPos().toFloat());
                entity.setColor(debugPointAction.getColor());
                animator.root.add(entity);
                return entity;
            });

            summonAction.setChildren(new Action[]{destroyAction});


            return new ExpandedAction(summonAction, destroyAction);
        }


        private static ExpandedAction convertScoreAction(com.gatdsen.simulation.action.Action action, Animator animator) {
            ScoreAction scoreAction = (ScoreAction) action;
            //ui Action
            MessageUiScoreAction indicateScoreChangeAction = new MessageUiScoreAction(0, animator.uiMessenger, scoreAction.getTeam(), scoreAction.getNewScore());

            return new ExpandedAction(indicateScoreChangeAction);
        }

        //ToDo: Add game specific actions

    }


    /**
     * Setzt eine Welt basierend auf den Daten in state auf und bereitet diese für nachfolgende Animationen vor
     *
     * @param viewport viewport used for rendering
     */
    public Animator(Viewport viewport, UiMessenger uiMessenger) {
        this.uiMessenger = uiMessenger;
        this.batch = new SpriteBatch();
        this.root = new Entity();

        setupView(viewport);

        setup();
        // assign textures to tiles after processing game Stage
        //put sprite information into gameStage?
    }

    @Override
    public void init(GameState state, String[] playerNames, String[][] skins) {
        synchronized (root) {
            this.state = state;
            mapPlayer0 = new TileMap(state, 0);
            mapPlayer1 = new TileMap(state, 1);
            root.clear();
            root.add(mapPlayer0);
            root.add(mapPlayer1);

            //ToDo: initialize based on gamestate data
        }
    }

    private void setup() {


        background = new SpriteEntity(
                IngameAssets.background,
                new Vector2(-backgroundViewport.getWorldWidth() / 2, -backgroundViewport.getWorldHeight() / 2),
                new Vector2(259, 128));


    }

    /**
     * Takes care of setting up the view for the user. Creates a new camera and sets the position to the center.
     * Adds it to the given Viewport.
     *
     * @param newViewport Viewport instance that animator will use to display the game.
     */
    private void setupView(Viewport newViewport) {

        int height = Gdx.graphics.getHeight();
        int width = Gdx.graphics.getWidth();
        this.viewport = newViewport;
        this.backgroundViewport = new FillViewport(259, 128);
        //center camera once
        //camera.position.set(Gdx.graphics.getWidth()/2f, Gdx.graphics.getHeight()/2f-12,0);
        //camera.zoom = 1;
        //viewport.setCamera(camera);
        //viewport.update(400,400);


        this.camera = new AnimatorCamera(30, 30f * width / height);
        this.viewport.setCamera(camera);
        camera.zoom = 1f;
        camera.position.set(new float[]{0, 0, 0});
        this.backgroundViewport.update(width, height);
        this.viewport.update(width, height, true);
        camera.update();

    }

    /**
     * Animates the logs actions
     *
     * @param log Queue aller {@link com.gatdsen.simulation.action.Action animations-relevanten Ereignisse}
     */
    public void animate(ActionLog log) {
        pendingLogs.add(log);
    }

    private Action convertAction(com.gatdsen.simulation.action.Action action) {
        return ActionConverters.convert(action, this);
    }

    @Override
    public void show() {

    }

    @Override
    public void render(float delta) {

        if (actionList.isEmpty()) {
            if (!pendingLogs.isEmpty()) {
                actionList.add(convertAction(pendingLogs.poll().getRootAction()));
            } else {
                synchronized (notificationObject) {
                    notificationObject.notifyAll();
                }
            }
        }


        ListIterator<Action> iter = actionList.listIterator();
        Stack<Remainder> remainders = new Stack<>();
        while (iter.hasNext()) {
            Action cur = iter.next();
            float remainder = cur.step(delta);
            if (remainder >= 0) {
                iter.remove();
                //Schedule children to run for the time not consumed by their parent
                Action[] children = cur.getChildren();
                if (children != null && children.length > 0) remainders.push(new Remainder(remainder, children));
            }
        }

        //Process the completed actions children with their respective remaining times
        while (!remainders.empty()) {
            Remainder curRemainder = remainders.pop();
            float remainingDelta = curRemainder.getRemainingDelta();
            Action[] actions = curRemainder.getActions();
            for (Action cur : actions) {
                float remainder = cur.step(remainingDelta);
                if (remainder >= 0) {
                    //Schedule children to run for the time that's not consumed by their parent
                    Action[] children = cur.getChildren();
                    if (children != null && children.length > 0) remainders.push(new Remainder(remainder, children));
                } else {
                    //Add the child to the list of running actions if not completed in the remaining time
                    actionList.add(cur);
                }
            }
        }


        camera.updateMovement(delta);
        camera.update();


        backgroundViewport.apply();
        //begin drawing elements of the SpriteBatch

        batch.setProjectionMatrix(backgroundViewport.getCamera().combined);
        batch.begin();
        background.draw(batch, delta, 1);
        batch.setProjectionMatrix(camera.combined);
        //tells the batch to render in the way specified by the camera
        // e.g. Coordinate-system and Viewport scaling
        viewport.apply();

        //ToDo: make one step in the scheduled actions


        //recursively draw all entities by calling the root group
        synchronized (root) {
            root.draw(batch, delta, 1);
        }
        batch.end();
    }

    @Override
    public void resize(int width, int height) {

        viewport.update(width, height, true);
        backgroundViewport.update(width, height);
        viewport.getCamera().update();
        camera.update();
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void hide() {

    }

    @Override
    public void dispose() {
        batch.dispose();
        //disposes the atlas for every class because it is passed down as a parameter, no bruno for changing back to menu
        //textureAtlas.dispose();

    }


    @Override
    public void awaitNotification() {
        synchronized (notificationObject) {
            try {
                notificationObject.wait();
            } catch (InterruptedException ignored) {
            }
        }
    }

}

