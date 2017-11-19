package co.selim.gameserver.entity;

import co.selim.gameserver.executor.GameExecutor;
import co.selim.gameserver.messaging.Messenger;
import co.selim.gameserver.model.GameMap;
import co.selim.gameserver.model.dto.outgoing.PlayerMoved;
import co.selim.gameserver.model.dto.outgoing.PlayerStopped;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static co.selim.gameserver.model.GameMap.MAP_SIZE;

public class Player implements GameEntity {
    private final Logger LOGGER = LoggerFactory.getLogger(Player.class);

    private static short nCount;
    private final GameExecutor executor;
    private final String id;
    private volatile boolean gameStarted;
    private Messenger messenger;

    private int xDirection;
    private int yDirection;

    private boolean movingX;
    private boolean movingY;

    private float moveDistance;

    private Body body;
    private final GameMap map;
    private final short GROUP_INDEX;

    private volatile Vector2 lastVelocity = new Vector2();

    private String name;
    private String skin;
    private int score;

    public Player(String address, GameMap map, Messenger messenger) {
        this.GROUP_INDEX = --nCount;
        this.id = UUID.randomUUID()
                .toString();
        executor = new GameExecutor("Player-" + address + "-UpdateExecutor");
        this.messenger = messenger;

        float x = MAP_SIZE.x / 2;
        float y = MAP_SIZE.y / 2;

        BodyDef bodyDef = new BodyDef();
        bodyDef.position.set(x, y);
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        float halfSize = 24;
        PolygonShape edgeShape = new PolygonShape();
        edgeShape.setAsBox(halfSize, halfSize);
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = edgeShape;
        fixtureDef.density = 0.5f;
        fixtureDef.friction = 0;
        fixtureDef.restitution = 0;
        fixtureDef.filter.groupIndex = GROUP_INDEX;
        body = map.createBody(bodyDef);
        body.createFixture(fixtureDef);
        body.setUserData(this);
        this.map = map;

        this.moveDistance = 15;

        executor.submitConnectionBoundTask(() -> {
            Vector2 velocity = new Vector2();

            if (movingX && movingY) {
                moveDistance *= Math.cos(Math.PI / 4);
            }

            if (movingX) {
                velocity.x = xDirection * moveDistance;
            } else {
                velocity.x = 0;
            }
            if (movingY) {
                velocity.y = yDirection * moveDistance;
            } else {
                velocity.y = 0;
            }

            Vector2 bodyPosition = body.getPosition();

            if (gameStarted && !lastVelocity.epsilonEquals(velocity) && velocity.isZero()) {
                messenger.broadCast(new PlayerStopped(bodyPosition.x, bodyPosition.y, getId()));
                lastVelocity.set(velocity);
            }

            body.setLinearVelocity(velocity);
            moveDistance = 15;

            float angle = MathUtils.atan2(velocity.y, velocity.x);

            if (!lastVelocity.epsilonEquals(velocity)) {
                messenger.broadCast(new PlayerMoved(bodyPosition.x, bodyPosition.y, angle,
                        moveDistance, getId()));
                lastVelocity.set(velocity);
            }
        });
    }

    public void move(int xDirection, int yDirection) {
        movingX = xDirection != 0;
        movingY = yDirection != 0;

        this.xDirection = xDirection;
        this.yDirection = yDirection;
    }

    public void throwSnowball(int pointerX, int pointerY) {
        new Snowball(executor, messenger, map, GROUP_INDEX, body.getPosition().x, body
                .getPosition().y, pointerX, pointerY);
    }

    public void disconnect() {
        destroy();
        executor.stop();
    }

    public Vector2 getPosition() {
        return new Vector2(body.getPosition());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSkin() {
        return skin;
    }

    public void setSkin(String skin) {
        this.skin = skin;
    }

    public void broadCastMessage(Object obj) {
        executor.submitOnce(() -> {
            messenger.broadCast(obj);
        });
    }

    public void sendMessage(Object obj) {
        executor.submitOnce(() -> {
            messenger.sendMessage(obj);
        });
    }

    @Override
    public void destroy() {
        map.destroyBody(body);
    }

    @Override
    public void collided(GameEntity other) {
        if (other.getType()
                .equals(Type.OBSTACLE) && (!movingX && !movingY)) {
            LOGGER.info("Player collided with obstacle and not moving diagonally, sending stop");
            executor.submitOnce(() -> {
                Vector2 bodyPos = body.getPosition();
                messenger.broadCast(new PlayerStopped(bodyPos.x, bodyPos.y, getId()));
            });
        }
    }

    public String getId() {
        return id;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void setGameStarted() {
        this.gameStarted = true;
    }

    @Override
    public Type getType() {
        return Type.PLAYER;
    }
}
