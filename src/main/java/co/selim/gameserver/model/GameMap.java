package co.selim.gameserver.model;

import co.selim.gameserver.entity.GameEntity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class GameMap {
    public static final Vector2 MAP_SIZE = new Vector2(1024, 1024);
    private static final Logger LOGGER = LoggerFactory.getLogger(GameMap.class);

    private final World world;
    private final ReentrantLock lock = new ReentrantLock();
    private final Set<Body> bodiesToRemove = ConcurrentHashMap.newKeySet();

    public GameMap() {
        Vector2 gravity = new Vector2(0, 0);
        this.world = new World(gravity, true);

        float mapWidth = MAP_SIZE.x;
        float mapHeight = MAP_SIZE.y;
        createWall(0, 0, 0, mapHeight);
        createWall(0, 0, mapWidth, 0);
        createWall(mapWidth, 0, mapWidth, mapHeight);
        createWall(0, mapHeight, mapWidth, mapHeight);

        world.setContactListener(new ContactListener() {
            @Override
            public void beginContact(Contact contact) {
                GameEntity a = (GameEntity) contact.getFixtureA()
                        .getBody()
                        .getUserData();
                GameEntity b = (GameEntity) contact.getFixtureB()
                        .getBody()
                        .getUserData();

                // TODO: fix native CME
                a.collided(b);
                b.collided(a);
            }

            @Override
            public void endContact(Contact contact) {

            }

            @Override
            public void preSolve(Contact contact, Manifold oldManifold) {

            }

            @Override
            public void postSolve(Contact contact, ContactImpulse impulse) {

            }
        });

        new Thread(() -> {
            while (true) {
                try {
                    lock.lock();
                    world.step(1.0f / 60.0f, 6, 3);
                    bodiesToRemove.forEach(world::destroyBody);
                    bodiesToRemove.clear();
                } finally {
                    lock.unlock();
                }
                try {
                    Thread.sleep(2L);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "WorldUpdateThread").start();
    }

    public Body createBody(BodyDef bodyDef) {
        return getInsideLock(world -> world.createBody(bodyDef));
    }

    public void destroyBody(Body body) {
        bodiesToRemove.add(body);
    }

    private Body getInsideLock(Function<World, Body> function) {
        try {
            lock.lock();
            return function.apply(world);
        } finally {
            lock.unlock();
        }
    }

    private void createWall(float x, float y, float w, float h) {
        // TODO: fix wall coordinates
        BodyDef bodyDef = new BodyDef();
        bodyDef.position.set(x - w / 2, y - h / 2);
        bodyDef.type = BodyDef.BodyType.StaticBody;
        PolygonShape edgeShape = new PolygonShape();
        edgeShape.setAsBox(w / 2, h / 2);
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = edgeShape;
        fixtureDef.density = 0.5f;
        fixtureDef.friction = 0;
        fixtureDef.restitution = 0;
        fixtureDef.filter.groupIndex = Short.MIN_VALUE;
        createBody(bodyDef).createFixture(fixtureDef)
                .getBody()
                .setUserData(new GameEntity() {
                    @Override
                    public void destroy() {
                    }

                    @Override
                    public void collided(GameEntity other) {
                        LOGGER.info("Someone collided with a wall");
                    }

                    @Override
                    public Type getType() {
                        return Type.OBSTACLE;
                    }
                });
    }
}
