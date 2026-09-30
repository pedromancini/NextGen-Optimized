import com.nextgen.optimizer.App;
import javafx.application.Application;
import javafx.animation.PauseTransition;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Opens the app, visits every page at a wide and a compact size and saves
 * screenshots to target/ui-*.png. Only reads state: nothing is applied.
 */
public class UiSmoke {
    static final String[] PAGES = {"dashboard", "full-opt", "tweaks", "cs2", "ram", "cleanup", "startup",
            "monitor", "network", "cpu", "gpu", "storage", "privacy", "overlay", "bios-tips", "settings"};

    public static void main(String[] args) { Application.launch(Preview.class, args); }

    public static class Preview extends Application {
        App app;
        Stage stage;
        final Deque<Runnable> steps = new ArrayDeque<>();

        public void start(Stage window) {
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> { e.printStackTrace(); System.exit(2); });
            try {
                stage = window;
                app = new App();
                app.start(window);
                stage.setWidth(1400); stage.setHeight(900);
                for (String page : PAGES) {
                    steps.add(() -> app.getNavigationManager().navigateTo(page));
                    steps.add(() -> capture("wide-" + page));
                }
                steps.add(() -> { stage.setWidth(1000); stage.setHeight(680); });
                for (String page : new String[]{"dashboard", "full-opt", "tweaks", "cs2", "ram"}) {
                    steps.add(() -> app.getNavigationManager().navigateTo(page));
                    steps.add(() -> capture("compact-" + page));
                }
                steps.add(() -> { System.out.println("UI SMOKE OK"); app.exitApplication(); });
                later(6, this::next);
            } catch (Throwable e) { e.printStackTrace(); System.exit(1); }
        }

        void next() {
            Runnable step = steps.poll();
            if (step == null) return;
            step.run();
            later(3, this::next);
        }

        void later(int seconds, Runnable runnable) {
            PauseTransition wait = new PauseTransition(Duration.seconds(seconds));
            wait.setOnFinished(e -> runnable.run()); wait.play();
        }

        void capture(String name) {
            try {
                var image = stage.getScene().snapshot(null);
                BufferedImage bitmap = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < bitmap.getHeight(); y++)
                    for (int x = 0; x < bitmap.getWidth(); x++) bitmap.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                ImageIO.write(bitmap, "png", new File("target/ui-" + name + ".png"));
                System.out.println("CAPTURE " + name);
            } catch (Exception e) { e.printStackTrace(); System.exit(1); }
        }
    }
}
