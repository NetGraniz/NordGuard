package dev.nordfjell.guard;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import org.bukkit.plugin.java.JavaPlugin;

/** Isolated-test helper. Invokes pure native shape clipping, never reads or changes a world. */
final class CollisionNativeProbe {
    private CollisionNativeProbe() {}

    static void run(JavaPlugin guard, Consumer<String> pass) throws Exception {
        var reflection = new Bindings(guard.getClass().getClassLoader());
        var random = new Random(0x26_02_C011L);
        for (int test = 0; test < 1000; test++) {
            double x = random.nextInt(17) - 8 + .2;
            double y = 64 + random.nextInt(9) * .125;
            double z = random.nextInt(17) - 8 + .2;
            double[] body = {x, y, z, x + .6, y + 1.8, z + .6};
            double[] motion = {(random.nextDouble() * 3) - 1.5,
                    (random.nextDouble() * 1.2) - .6, (random.nextDouble() * 3) - 1.5};
            if (test % 7 == 0) motion[0] = 0;
            if (test % 11 == 0) motion[1] = 0;
            if (test % 13 == 0) motion[2] = 0;
            if (test % 5 == 0) motion[2] = motion[0];
            int count = test % 17 == 0 ? 0 : 1 + random.nextInt(32);
            var boxes = new ArrayList<double[]>(count);
            for (int i = 0; i < count; i++) {
                double[] box = new double[6];
                for (int axis = 0; axis < 3; axis++) {
                    box[axis] = body[axis] - 1.5 + random.nextDouble() * 3;
                    box[axis + 3] = box[axis] + .125 + random.nextDouble() * 1.875;
                }
                // Force at least one separating axis: native and replica start outside all obstacles.
                int axis = random.nextInt(3);
                double width = box[axis + 3] - box[axis];
                double gap = random.nextInt(5) * .125;
                if (random.nextBoolean()) {
                    box[axis] = body[axis + 3] + gap;
                    box[axis + 3] = box[axis] + width;
                } else {
                    box[axis + 3] = body[axis] - gap;
                    box[axis] = box[axis + 3] - width;
                }
                boxes.add(box);
            }
            reflection.compare(test, body, motion, boxes);
        }
        pass.accept("native_axis_clipping_1000_cases");
    }

    /** Resolve once per suite; GuardProbe and NordGuard have different plugin class loaders. */
    private static final class Bindings {
        final Constructor<?> nativeBox, nativeVector, localBox, localMotion, localScene;
        final Method createShape, nativeClip, localMove, displacement;
        final Method[] localCoordinates;
        final Field[] nativeCoordinates;

        Bindings(ClassLoader guardLoader) throws Exception {
            Class<?> aabb = Class.forName("net.minecraft.world.phys.AABB", true, guardLoader);
            Class<?> vector = Class.forName("net.minecraft.world.phys.Vec3", true, guardLoader);
            Class<?> shapes = Class.forName("net.minecraft.world.phys.shapes.Shapes", true, guardLoader);
            Class<?> entity = Class.forName("net.minecraft.world.entity.Entity", true, guardLoader);
            nativeBox = aabb.getConstructor(double.class, double.class, double.class,
                    double.class, double.class, double.class);
            nativeVector = vector.getConstructor(double.class, double.class, double.class);
            createShape = shapes.getMethod("create", aabb);
            nativeClip = entity.getDeclaredMethod("collideWithShapes", vector, aabb, List.class);
            nativeClip.setAccessible(true);
            nativeCoordinates = new Field[]{vector.getField("x"), vector.getField("y"), vector.getField("z")};

            Class<?> box = Class.forName("dev.nordfjell.guard.Geometry$Box", true, guardLoader);
            Class<?> motion = Class.forName("dev.nordfjell.guard.CollisionPhysics$Motion", true, guardLoader);
            Class<?> scene = Class.forName("dev.nordfjell.guard.CollisionPhysics$Scene", true, guardLoader);
            Class<?> result = Class.forName("dev.nordfjell.guard.CollisionPhysics$Result", true, guardLoader);
            Class<?> collision = Class.forName("dev.nordfjell.guard.CollisionPhysics", true, guardLoader);
            localBox = box.getDeclaredConstructor(double.class, double.class, double.class,
                    double.class, double.class, double.class);
            localMotion = motion.getDeclaredConstructor(double.class, double.class, double.class);
            localScene = scene.getDeclaredConstructor(List.class);
            localBox.setAccessible(true); localMotion.setAccessible(true); localScene.setAccessible(true);
            localMove = collision.getDeclaredMethod("move", box, motion, motion, boolean.class, float.class, scene);
            localMove.setAccessible(true);
            displacement = result.getDeclaredMethod("displacement"); displacement.setAccessible(true);
            localCoordinates = new Method[]{motion.getDeclaredMethod("x"), motion.getDeclaredMethod("y"), motion.getDeclaredMethod("z")};
            for (var method : localCoordinates) method.setAccessible(true);
        }

        private static Object box(Constructor<?> constructor, double[] bounds) throws Exception {
            return constructor.newInstance(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
        }

        void compare(int test, double[] bounds, double[] requested, List<double[]> obstacles) throws Exception {
            var nativeShapes = new ArrayList<Object>(obstacles.size());
            var localShapes = new ArrayList<Object>(obstacles.size());
            for (var obstacle : obstacles) {
                nativeShapes.add(createShape.invoke(null, box(nativeBox, obstacle)));
                localShapes.add(box(localBox, obstacle));
            }
            Object nativeMotion = nativeVector.newInstance(requested[0], requested[1], requested[2]);
            Object nativeResult = nativeClip.invoke(null, nativeMotion, box(nativeBox, bounds), nativeShapes);
            Object requestedMotion = localMotion.newInstance(requested[0], requested[1], requested[2]);
            Object result = localMove.invoke(null, box(localBox, bounds), requestedMotion, requestedMotion,
                    false, 0f, localScene.newInstance(localShapes));
            Object clipped = displacement.invoke(result);
            for (int axis = 0; axis < 3; axis++) {
                double expected = nativeCoordinates[axis].getDouble(nativeResult);
                double actual = ((Number) localCoordinates[axis].invoke(clipped)).doubleValue();
                if (!Double.isFinite(actual) || Math.abs(expected - actual) > 1E-7)
                    throw new AssertionError("Native collision mismatch at case " + test + ", axis " + axis
                            + ": expected=" + expected + ", actual=" + actual
                            + ", body=" + Arrays.toString(bounds) + ", motion=" + Arrays.toString(requested));
            }
        }
    }
}
